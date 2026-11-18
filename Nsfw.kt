package com.example.screenguard

import android.content.Context
import android.graphics.Bitmap
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder

// ছবির স্কোর বের করে (০ = নিরাপদ, ১ = আপত্তিকর)
// assets/nsfw_model.tflite ফাইল লাগে। না থাকলে ready = false
class Nsfw(c: Context) {

    companion object {
        const val FILE = "nsfw_model.tflite"
        fun available(c: Context) = c.assets.list("")?.contains(FILE) == true
    }

    private var ip: Interpreter? = null
    private var size = 224
    private var isFloat = true
    private var outN = 5

    val ready: Boolean get() = ip != null

    init {
        try {
            val bytes = c.assets.open(FILE).use { it.readBytes() }
            val bb = ByteBuffer.allocateDirect(bytes.size).order(ByteOrder.nativeOrder())
            bb.put(bytes)
            bb.rewind()
            val x = Interpreter(bb, Interpreter.Options().setNumThreads(2))
            size = x.getInputTensor(0).shape()[1]
            isFloat = x.getInputTensor(0).dataType() == DataType.FLOAT32
            outN = x.getOutputTensor(0).shape().last()
            ip = x
        } catch (e: Exception) {
            ip = null
        }
    }

    fun score(bmp: Bitmap): Float {
        val x = ip ?: return 0f
        return try {
            val b = Bitmap.createScaledBitmap(bmp, size, size, true)
            val px = IntArray(size * size)
            b.getPixels(px, 0, size, 0, 0, size, size)
            val input = ByteBuffer
                .allocateDirect(size * size * 3 * (if (isFloat) 4 else 1))
                .order(ByteOrder.nativeOrder())
            for (p in px) {
                val r = (p shr 16) and 0xFF
                val g = (p shr 8) and 0xFF
                val bl = p and 0xFF
                if (isFloat) {
                    input.putFloat(r / 255f)
                    input.putFloat(g / 255f)
                    input.putFloat(bl / 255f)
                } else {
                    input.put(r.toByte())
                    input.put(g.toByte())
                    input.put(bl.toByte())
                }
            }
            input.rewind()
            val out = Array(1) { FloatArray(outN) }
            x.run(input, out)
            val o = out[0]
            when (outN) {
                // ক্রম: drawings, hentai, neutral, porn, sexy
                5 -> o[3] + o[1] + 0.5f * o[4]
                2 -> o[1]
                else -> 0f
            }
        } catch (e: Exception) {
            0f
        }
    }

    fun close() {
        try { ip?.close() } catch (e: Exception) {}
    }
}
