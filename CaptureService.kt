package com.example.screenguard

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager

// স্ক্রিনের ছোট ছবি নিয়ে প্রতি ~০.৭ সেকেন্ডে AI দিয়ে চেক করে (ছবি ও ভিডিও)
class CaptureService : Service() {

    companion object {
        @Volatile var running = false
    }

    private var proj: MediaProjection? = null
    private var vd: VirtualDisplay? = null
    private var reader: ImageReader? = null
    private var nsfw: Nsfw? = null
    private var thread: HandlerThread? = null
    private var handler: Handler? = null

    private val loop = object : Runnable {
        override fun run() {
            try { scan() } catch (e: Exception) {}
            handler?.postDelayed(this, 700)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val n = Notif.build(this, "ছবি ও ভিডিও পাহারা চলছে")
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(2, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else {
            startForeground(2, n)
        }
        if (running) return START_NOT_STICKY

        val code = intent?.getIntExtra("code", 0) ?: 0
        @Suppress("DEPRECATION")
        val data = intent?.getParcelableExtra<Intent>("data")
        if (data == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        val model = Nsfw(this)
        if (!model.ready) {
            stopSelf()
            return START_NOT_STICKY
        }
        nsfw = model
        try { begin(code, data) } catch (e: Exception) { stopSelf() }
        return START_NOT_STICKY
    }

    private fun begin(code: Int, data: Intent) {
        val mpm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val p = mpm.getMediaProjection(code, data)
        // Android 14+ এ ক্যাপচারের আগে callback দিতেই হয়
        p.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() { stopSelf() }
        }, Handler(Looper.getMainLooper()))
        proj = p

        val dm = resources.displayMetrics
        val w = maxOf(dm.widthPixels / 3, 120)
        val h = maxOf(dm.heightPixels / 3, 120)
        val r = ImageReader.newInstance(w, h, PixelFormat.RGBA_8888, 2)
        reader = r
        vd = p.createVirtualDisplay(
            "sg", w, h, dm.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, r.surface, null, null
        )

        val t = HandlerThread("sg-cap")
        t.start()
        thread = t
        handler = Handler(t.looper)
        running = true
        handler?.post(loop)
    }

    private fun scan() {
        val r = reader ?: return
        val img = r.acquireLatestImage() ?: return
        try {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            val g = GuardService.instance
            if (!pm.isInteractive || g == null || g.blocking || !Store.enabled(this)) return

            val iw = img.width
            val ih = img.height
            val pl = img.planes[0]
            val bmp = Bitmap.createBitmap(pl.rowStride / pl.pixelStride, ih, Bitmap.Config.ARGB_8888)
            bmp.copyPixelsFromBuffer(pl.buffer)
            val crop = Bitmap.createBitmap(bmp, 0, 0, iw, ih)
            val score = nsfw?.score(crop) ?: 0f
            if (score >= Store.threshold(this)) g.trigger()
        } finally {
            img.close()
        }
    }

    override fun onDestroy() {
        running = false
        handler?.removeCallbacksAndMessages(null)
        try { vd?.release() } catch (e: Exception) {}
        try { reader?.close() } catch (e: Exception) {}
        try { proj?.stop() } catch (e: Exception) {}
        thread?.quitSafely()
        nsfw?.close()
        super.onDestroy()
    }
}
