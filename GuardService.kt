package com.example.screenguard

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

// ব্লক পর্দা: Back চাপলে এই ভিউ থেকে সংকেত যায়
private class Cover(c: Context, val onBack: () -> Unit) : FrameLayout(c) {
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_BACK) {
            if (event.action == KeyEvent.ACTION_UP) onBack()
            return true
        }
        return super.dispatchKeyEvent(event)
    }
}

class GuardService : AccessibilityService() {

    companion object {
        @Volatile var instance: GuardService? = null
    }

    @Volatile private var overlay: View? = null
    val blocking: Boolean get() = overlay != null

    private var lastScan = 0L
    private var reel = false
    private val h = Handler(Looper.getMainLooper())

    // রিলস/শর্টস ধরার চেষ্টা (পরীক্ষামূলক, অ্যাপ আপডেটে বদলাতে পারে)
    private val reelPkgs = setOf(
        "com.google.android.youtube", "com.facebook.katana", "com.instagram.android"
    )
    private val reelIds = listOf(
        "reel_player", "reel_recycler", "shorts_player", "reels_viewer", "clips_viewer"
    )
    private val reelUrls = listOf("youtube.com/shorts", "/reels/", "/reel/")
    private val tiktok = setOf("com.zhiliaoapp.musically", "com.ss.android.ugc.trill")

    private val quotes = listOf(
        "একটা গভীর শ্বাস নাও। এই মুহূর্তটা পেরিয়ে গেলেই সহজ হয়ে যাবে।",
        "তুমি নিজের কাছে কথা দিয়েছিলে। কথা রাখো।",
        "আজকের জয়টাই কালকের শক্তি।",
        "উঠে হাঁটো, পানি খাও, অন্য কিছু করো।"
    )

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Notif.update(this)
    }

    override fun onAccessibilityEvent(e: AccessibilityEvent?) {
        if (e == null) return
        val pkg = e.packageName?.toString() ?: return
        if (pkg == packageName) return
        if (overlay != null || !Store.enabled(this)) return
        val now = System.currentTimeMillis()
        if (now - lastScan < 600) return
        lastScan = now

        // টিকটক = পুরোটাই শর্ট ভিডিও
        if (Store.reelOn(this) && tiktok.contains(pkg)) {
            trigger()
            return
        }

        val root = rootInActiveWindow ?: return
        reel = false
        val sb = StringBuilder()
        collect(root, sb, root.packageName?.toString() ?: pkg)
        val raw = sb.toString()

        if (Store.reelOn(this)) {
            val low = raw.lowercase()
            if (reel || reelUrls.any { low.contains(it) }) {
                trigger()
                return
            }
        }

        if (Store.txtOn(this)) {
            var text = norm(raw)
            for (w in Store.white(this).map { norm(it) }.filter { it.isNotEmpty() }) {
                text = text.replace(w, " ")
            }
            val tags = Store.words(this).map { norm(it) }.filter { it.isNotEmpty() }
            if (tags.any { text.contains(it) }) trigger()
        }
    }

    // যেকোনো থ্রেড থেকে ডাকা যায় (ছবি/ভিডিও ডিটেকশনও এটাই ডাকে)
    fun trigger() {
        h.post {
            if (overlay != null || !Store.enabled(this)) return@post
            Store.addBlock(this)
            Notif.update(this)
            showCover()
        }
    }

    private fun dp(n: Int) = (n * resources.displayMetrics.density).toInt()

    private fun showCover() {
        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val blur = Store.style(this) == 1
        val cover = Cover(this) { dismiss() }
        cover.setBackgroundColor(
            if (!blur) Color.parseColor("#0B1020")
            else if (Build.VERSION.SDK_INT >= 31) Color.parseColor("#B30B1020")
            else Color.parseColor("#F20B1020")
        )

        fun label(t: String, size: Float, color: Int, bold: Boolean) = TextView(this).apply {
            text = t
            textSize = size
            setTextColor(color)
            gravity = Gravity.CENTER
            if (bold) typeface = Typeface.DEFAULT_BOLD
        }

        fun lpTop(n: Int) = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(n) }

        val logo = ImageView(this).apply {
            setImageResource(R.drawable.ic_shield)
            setPadding(dp(24), dp(24), dp(24), dp(24))
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#6366F1"))
            }
        }

        val back = TextView(this).apply {
            text = "← ফিরে যান"
            textSize = 17f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                cornerRadius = dp(16).toFloat()
                setColor(Color.parseColor("#6366F1"))
            }
            setPadding(dp(40), dp(14), dp(40), dp(14))
            setOnClickListener { dismiss() }
        }

        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(32), dp(32), dp(32), dp(32))
            addView(logo, LinearLayout.LayoutParams(dp(104), dp(104)))
            addView(label("এটা এখন ব্লক করা হয়েছে", 26f, Color.WHITE, true), lpTop(28))
            addView(label(quotes.random(), 16f, Color.parseColor("#B8C0E0"), false), lpTop(12))
            addView(back, lpTop(36))
            addView(label("অথবা Back বাটন চাপুন", 13f, Color.parseColor("#8A93B2"), false), lpTop(14))
        }
        cover.addView(
            col,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            if (blur) PixelFormat.TRANSLUCENT else PixelFormat.OPAQUE
        )
        if (blur && Build.VERSION.SDK_INT >= 31) {
            lp.flags = lp.flags or WindowManager.LayoutParams.FLAG_BLUR_BEHIND
            lp.blurBehindRadius = 90
        }
        try {
            wm.addView(cover, lp)
        } catch (ex: Exception) {
            return
        }
        overlay = cover
        cover.isFocusable = true
        cover.isFocusableInTouchMode = true
        cover.requestFocus()
    }

    // Back চাপলে পর্দা সরে যায় এবং আগের পেজে ফেরা হয়
    private fun dismiss() {
        val o = overlay ?: return
        overlay = null
        try {
            (getSystemService(Context.WINDOW_SERVICE) as WindowManager).removeView(o)
        } catch (ex: Exception) {}
        performGlobalAction(GLOBAL_ACTION_BACK)
    }

    // ফোনের Back বাটনের চাপ ধরা (পর্দা থাকাকালীন)
    override fun onKeyEvent(event: KeyEvent?): Boolean {
        if (event != null && overlay != null && event.keyCode == KeyEvent.KEYCODE_BACK) {
            if (event.action == KeyEvent.ACTION_UP) h.post { dismiss() }
            return true
        }
        return false
    }

    private fun collect(n: AccessibilityNodeInfo?, sb: StringBuilder, pkg: String) {
        if (n == null) return
        n.text?.let { sb.append(it).append(' ') }
        n.contentDescription?.let { sb.append(it).append(' ') }
        if (reelPkgs.contains(pkg)) {
            val id = n.viewIdResourceName
            if (id != null && reelIds.any { id.contains(it, true) }) reel = true
        }
        for (i in 0 until n.childCount) collect(n.getChild(i), sb, pkg)
    }

    private fun norm(s: String) = s.lowercase()
        .replace('0', 'o').replace('1', 'i').replace('3', 'e')
        .replace('@', 'a').replace('$', 's')
        .replace(Regex("[^\\p{L}\\p{M}\\s]"), " ")
        .replace(Regex("\\s+"), " ").trim()

    override fun onInterrupt() {}

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        instance = null
        Notif.cancel(this)
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        instance = null
        h.removeCallbacksAndMessages(null)
        val o = overlay
        if (o != null) {
            try {
                (getSystemService(Context.WINDOW_SERVICE) as WindowManager).removeView(o)
            } catch (ex: Exception) {}
            overlay = null
        }
        super.onDestroy()
    }
}
