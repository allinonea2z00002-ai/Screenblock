package com.example.screenguard

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.*

class MainActivity : Activity() {

    private lateinit var box: LinearLayout
    private var unlocked = false
    private var askedNotif = false

    private val BG = Color.parseColor("#0B1020")
    private val CARD = Color.parseColor("#151B2E")
    private val FIELD = Color.parseColor("#1F2742")
    private val ACC = Color.parseColor("#6366F1")
    private val GREEN = Color.parseColor("#22C55E")
    private val RED = Color.parseColor("#EF4444")
    private val MUTED = Color.parseColor("#9AA3C0")
    private val TXT = Color.WHITE

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        window.statusBarColor = BG
        window.navigationBarColor = BG
        box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(28), dp(16), dp(32))
        }
        setContentView(ScrollView(this).apply {
            setBackgroundColor(BG)
            addView(box)
        })
        val pin = Store.pin(this)
        if (pin == null) build() else askPin(pin)
    }

    override fun onResume() {
        super.onResume()
        if (unlocked) build()
    }

    // ---------- ছোট সাহায্যকারী ফাংশন ----------
    private fun dp(n: Int) = (n * resources.displayMetrics.density).toInt()
    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_LONG).show()
    private fun again() { box.post { build() } }

    private fun rounded(color: Int, r: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(r).toFloat()
    }

    private fun gradient(c1: Int, c2: Int, r: Int) = GradientDrawable().apply {
        orientation = GradientDrawable.Orientation.LEFT_RIGHT
        colors = intArrayOf(c1, c2)
        cornerRadius = dp(r).toFloat()
    }

    private fun tv(t: String, size: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
        text = t
        textSize = size
        setTextColor(color)
        if (bold) setTypeface(typeface, Typeface.BOLD)
    }

    private fun gap(n: Int) = View(this).apply { layoutParams = LinearLayout.LayoutParams(1, dp(n)) }

    private fun card(v: List<View>, bg: android.graphics.drawable.Drawable? = null) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = bg ?: rounded(CARD, 20)
        setPadding(dp(16), dp(16), dp(16), dp(16))
        layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = dp(12) }
        v.forEach { addView(it) }
    }

    private fun button(t: String, color: Int, f: () -> Unit) = TextView(this).apply {
        text = t
        textSize = 16f
        setTextColor(Color.WHITE)
        setTypeface(typeface, Typeface.BOLD)
        gravity = Gravity.CENTER
        background = rounded(color, 14)
        setPadding(dp(16), dp(14), dp(16), dp(14))
        layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(10) }
        setOnClickListener { f() }
    }

    private fun field(h: String) = EditText(this).apply {
        hint = h
        setHintTextColor(MUTED)
        setTextColor(Color.WHITE)
        textSize = 16f
        background = rounded(FIELD, 12)
        setPadding(dp(14), dp(12), dp(14), dp(12))
        setSingleLine()
    }

    private fun pills(items: List<String>, sel: Int, f: (Int) -> Unit) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        items.forEachIndexed { i, s ->
            addView(TextView(this@MainActivity).apply {
                text = s
                textSize = 14f
                gravity = Gravity.CENTER
                setTextColor(if (i == sel) Color.WHITE else MUTED)
                background = rounded(if (i == sel) ACC else FIELD, 12)
                setPadding(dp(8), dp(10), dp(8), dp(10))
                layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply {
                    if (i > 0) leftMargin = dp(8)
                }
                setOnClickListener { f(i) }
            })
        }
    }

    private fun toggle(title: String, desc: String, on: Boolean, f: (Boolean) -> Unit): View {
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
            addView(tv(title, 16f, TXT, true))
            addView(tv(desc, 13f, MUTED))
        }
        val states = arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf())
        val sw = Switch(this).apply {
            isChecked = on
            thumbTintList = ColorStateList(states, intArrayOf(Color.WHITE, Color.parseColor("#8A93B2")))
            trackTintList = ColorStateList(states, intArrayOf(ACC, Color.parseColor("#2A3150")))
            setOnCheckedChangeListener { _, v -> f(v) }
        }
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(8), 0, dp(8))
            addView(col)
            addView(sw)
        }
    }

    private fun status(t: String, ok: Boolean, action: String? = null, f: (() -> Unit)? = null): View {
        val dot = View(this).apply {
            background = rounded(if (ok) GREEN else RED, 6)
            layoutParams = LinearLayout.LayoutParams(dp(12), dp(12)).apply { rightMargin = dp(12) }
        }
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(8), 0, dp(8))
            addView(dot)
            addView(tv(t, 15f, TXT).apply { layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f) })
            if (!ok && action != null && f != null) {
                addView(tv(action, 14f, ACC, true).apply {
                    setPadding(dp(8), dp(4), 0, dp(4))
                    setOnClickListener { f() }
                })
            }
        }
    }

    private fun stat(label: String, value: String) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
        addView(tv(value, 22f, TXT, true))
        addView(tv(label, 13f, MUTED))
    }

    private fun chipRow(t: String, del: () -> Unit) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        background = rounded(FIELD, 12)
        setPadding(dp(14), dp(8), dp(8), dp(8))
        layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(8) }
        addView(tv(t, 16f, TXT).apply { layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f) })
        addView(tv("✕", 18f, MUTED).apply {
            setPadding(dp(14), dp(6), dp(14), dp(6))
            setOnClickListener { del() }
        })
    }

    // ---------- PIN ----------
    private fun askPin(pin: String) {
        val et = EditText(this).apply {
            hint = "PIN"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
        }
        AlertDialog.Builder(this).setTitle("PIN দিন").setView(et).setCancelable(false)
            .setPositiveButton("ঠিক আছে") { _, _ ->
                if (et.text.toString() == pin) build() else { toast("ভুল PIN"); finish() }
            }
            .setNegativeButton("বাতিল") { _, _ -> finish() }
            .show()
    }

    // ---------- ছবি/ভিডিও পাহারা চালু/বন্ধ ----------
    private fun startImg() {
        if (!Nsfw.available(this)) {
            toast("আগে nsfw_model.tflite ফাইল যোগ করুন")
            again()
            return
        }
        val mpm = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        startActivityForResult(mpm.createScreenCaptureIntent(), 77)
    }

    private fun stopImg() {
        stopService(Intent(this, CaptureService::class.java))
        box.postDelayed({ build() }, 500)
    }

    // ছবি বা ভিডিও যেকোনো একটা চালু থাকলেই AI পাহারা চলবে, দুটো বন্ধ হলে থামবে
    private fun syncImg() {
        if (Store.photoOn(this) || Store.videoOn(this)) {
            if (!CaptureService.running) startImg()
        } else stopImg()
    }

    override fun onActivityResult(req: Int, res: Int, data: Intent?) {
        super.onActivityResult(req, res, data)
        if (req == 77) {
            if (res == RESULT_OK && data != null) {
                startForegroundService(
                    Intent(this, CaptureService::class.java).putExtra("code", res).putExtra("data", data)
                )
                box.postDelayed({ build() }, 900)
            } else {
                toast("স্ক্রিন ক্যাপচারের অনুমতি দেওয়া হয়নি")
                again()
            }
        }
    }

    // ---------- মূল স্ক্রিন ----------
    private fun build() {
        unlocked = true
        val c = this
        box.removeAllViews()
        val on = Store.enabled(c)

        if (!askedNotif && Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            askedNotif = true
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 5)
        }

        // হেডার
        val logo = ImageView(c).apply {
            setImageResource(R.drawable.ic_shield)
            background = rounded(ACC, 16)
            setPadding(dp(10), dp(10), dp(10), dp(10))
            layoutParams = LinearLayout.LayoutParams(dp(52), dp(52)).apply { rightMargin = dp(14) }
        }
        box.addView(LinearLayout(c).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), 0, 0, dp(18))
            addView(logo)
            addView(LinearLayout(c).apply {
                orientation = LinearLayout.VERTICAL
                addView(tv("ScreenGuard", 24f, TXT, true))
                addView(tv("নিজেকে রক্ষার সঙ্গী", 14f, MUTED))
            })
        })

        // অন/অফ
        box.addView(card(listOf(
            tv(if (on) "সুরক্ষা চালু আছে" else "সুরক্ষা বন্ধ আছে", 20f, if (on) TXT else RED, true),
            tv(if (on) "লিখা, ছবি ও ভিডিও পাহারা চলছে" else "এখন কিছুই ব্লক হচ্ছে না", 14f, if (on) Color.parseColor("#E0E7FF") else MUTED),
            button(if (on) "বন্ধ করুন" else "চালু করুন", if (on) RED else GREEN) {
                if (on) {
                    AlertDialog.Builder(c).setTitle("সুরক্ষা বন্ধ করবেন?")
                        .setMessage("বন্ধ করলে কিছুই ব্লক হবে না।")
                        .setPositiveButton("বন্ধ করুন") { _, _ ->
                            Store.setEnabled(c, false); Notif.update(c); again()
                        }
                        .setNegativeButton("থাকুক", null).show()
                } else {
                    Store.setEnabled(c, true); Notif.update(c); again()
                }
            }
        ),
            if (on) gradient(Color.parseColor("#4F46E5"), Color.parseColor("#7C3AED"), 20) else null
        ))

        // স্ট্রিক
        box.addView(card(listOf(
            tv("টানা পরিষ্কার", 14f, MUTED),
            tv("${bn(Store.streak(c))} দিন", 44f, TXT, true),
            gap(12),
            LinearLayout(c).apply {
                orientation = LinearLayout.HORIZONTAL
                addView(stat("আজ ব্লক", bn(Store.todayCount(c))))
                addView(stat("সর্বোচ্চ স্ট্রিক", "${bn(Store.best(c))} দিন"))
            }
        )))

        // সেটআপের অবস্থা
        val hasNotif = Build.VERSION.SDK_INT < 33 ||
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        val rows = mutableListOf<View>(
            tv("সেটআপ", 18f, TXT, true),
            status("Accessibility সার্ভিস", GuardService.instance != null, "চালু করুন") {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            },
            status("নোটিফিকেশন অনুমতি", hasNotif, "অনুমতি দিন") {
                if (Build.VERSION.SDK_INT >= 33) {
                    requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 5)
                }
            },
            status("ছবি/ভিডিও AI মডেল ফাইল", Nsfw.available(c))
        )
        if (!Nsfw.available(c)) {
            rows.add(tv("nsfw_model.tflite ফাইলটা GitHub রিপোর রুটে দিন, তাহলে ছবি/ভিডিও ডিটেকশন কাজ করবে। লিখা আর রিলস ব্লক মডেল ছাড়াই চলে।", 12f, MUTED))
        }
        box.addView(card(rows))

        // কিসের জন্য ব্লক
        box.addView(card(listOf(
            tv("কী ব্লক হবে", 18f, TXT, true),
            toggle("লিখা", "গল্প, সাইট, সার্চে খারাপ শব্দ", Store.txtOn(c)) { Store.setTxtOn(c, it) },
            toggle("ছবি", "স্ক্রিনের ছবি AI দিয়ে চেক", Store.photoOn(c)) { v ->
                Store.setPhotoOn(c, v); syncImg()
            },
            toggle("ভিডিও", "চলমান ভিডিও AI দিয়ে চেক", Store.videoOn(c)) { v ->
                Store.setVideoOn(c, v); syncImg()
            },
            toggle("রিলস / শর্টস", "YouTube, Facebook, Instagram, TikTok", Store.reelOn(c)) {
                Store.setReelOn(c, it)
            },
            gap(10),
            tv("ব্লকের ধরন", 14f, MUTED),
            gap(6),
            pills(listOf("পুরো ব্লক", "ব্লার"), Store.style(c)) { Store.setStyle(c, it); again() },
            gap(14),
            tv("ছবির সংবেদনশীলতা", 14f, MUTED),
            gap(6),
            pills(listOf("কম", "মাঝারি", "বেশি"), Store.sens(c)) { Store.setSens(c, it); again() },
            gap(14),
            tv("ব্লক বা ব্লার পর্দা Back বাটন চাপলেই সরে যাবে — ভাইব্রেশন বা অপেক্ষা নেই।", 13f, MUTED)
        )))

        // কীওয়ার্ড
        val kw = field("নতুন কীওয়ার্ড (যেমন: choti golpo)")
        val kwList = mutableListOf<View>(
            tv("কীওয়ার্ড", 18f, TXT, true),
            tv("এই শব্দ স্ক্রিনে এলেই ব্লক হবে। বাংলা ও Banglish দুটোই দিন।", 13f, MUTED),
            gap(8),
            kw,
            button("যোগ করুন", ACC) {
                val t = kw.text.toString().trim().replace(Regex("[\t\n]"), " ")
                if (t.isNotEmpty()) {
                    val l = Store.words(c)
                    if (!l.contains(t)) { l.add(t); Store.saveWords(c, l) }
                    again()
                }
            },
            button("অ্যাডাল্ট শব্দ যোগ করুন", FIELD) {
                Store.addAdultWords(c)
                toast("অ্যাডাল্ট শব্দ যোগ হয়েছে")
                again()
            }
        )
        for (w in Store.words(c)) {
            kwList.add(chipRow(w) {
                val l = Store.words(c)
                l.remove(w)
                Store.saveWords(c, l)
                again()
            })
        }
        box.addView(card(kwList))

        // ছাড়ের শব্দ
        val wi = field("যেমন: essex")
        val wList = mutableListOf<View>(
            tv("ছাড়ের শব্দ", 18f, TXT, true),
            tv("এই শব্দ থাকলে ভুল ব্লক হবে না।", 13f, MUTED),
            gap(8),
            wi,
            button("ছাড় যোগ করুন", FIELD) {
                val t = wi.text.toString().trim().replace(Regex("[\t\n]"), " ")
                if (t.isNotEmpty()) {
                    val l = Store.white(c)
                    if (!l.contains(t)) { l.add(t); Store.saveWhite(c, l) }
                    again()
                }
            }
        )
        for (w in Store.white(c)) {
            wList.add(chipRow(w) {
                val l = Store.white(c)
                l.remove(w)
                Store.saveWhite(c, l)
                again()
            })
        }
        box.addView(card(wList))

        // PIN
        val pn = field("নতুন PIN (সংখ্যা)").apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
        }
        val pinList = mutableListOf<View>(
            tv("নিরাপত্তা", 18f, TXT, true),
            tv("PIN দিলে অ্যাপ খুলতে PIN লাগবে। ভুললে অ্যাপের ডাটা মুছতে হবে।", 13f, MUTED),
            gap(8),
            pn,
            button("PIN সেট করুন", ACC) {
                if (pn.text.length >= 4) {
                    Store.setPin(c, pn.text.toString())
                    toast("PIN সেট হয়েছে")
                    again()
                } else toast("কমপক্ষে ৪ সংখ্যা দিন")
            }
        )
        if (Store.pin(c) != null) {
            pinList.add(button("PIN মুছুন", FIELD) {
                Store.setPin(c, null)
                toast("PIN মুছে গেছে")
                again()
            })
        }
        box.addView(card(pinList))
    }
}
