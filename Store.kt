package com.example.screenguard

import android.content.Context
import java.time.LocalDate
import java.time.temporal.ChronoUnit

// ইংরেজি সংখ্যাকে বাংলায় বদলায়
fun bn(x: Any): String =
    x.toString().map { if (it in '0'..'9') '০' + (it - '0') else it }.joinToString("")

// সব সেটিং ফোনের ভেতরে সেভ থাকে, ইন্টারনেটে কিছু যায় না
object Store {

    private val DEFAULT = listOf(
        "choti golpo", "choti", "porn", "xxx", "xnxx", "xvideos", "nude",
        "চটি", "পর্ন", "যৌন গল্প", "সেক্স"
    )

    // অ্যাডাল্ট প্রিসেট — এক চাপে সব যোগ হয়
    val ADULT = listOf(
        "porn", "xxx", "xnxx", "xvideos", "xhamster", "pornhub", "redtube",
        "sex", "nude", "naked", "erotic", "hentai", "onlyfans",
        "পর্ন", "চটি", "যৌন", "সেক্স", "নগ্ন", "অশ্লীল"
    )

    fun addAdultWords(c: Context) {
        val l = words(c)
        for (w in ADULT) if (!l.contains(w)) l.add(w)
        saveWords(c, l)
    }

    private fun p(c: Context) = c.getSharedPreferences("sg", Context.MODE_PRIVATE)
    private fun today() = LocalDate.now().toString()

    // ---------- কীওয়ার্ড ----------
    fun words(c: Context): MutableList<String> {
        val s = p(c).getString("kw", null) ?: return DEFAULT.toMutableList()
        return s.split("\n").filter { it.isNotBlank() }.toMutableList()
    }

    fun saveWords(c: Context, l: List<String>) {
        p(c).edit().putString("kw", l.joinToString("\n")).apply()
    }

    // ---------- ছাড়ের শব্দ ----------
    fun white(c: Context): MutableList<String> =
        (p(c).getString("white", "") ?: "").split("\n").filter { it.isNotBlank() }.toMutableList()

    fun saveWhite(c: Context, l: List<String>) {
        p(c).edit().putString("white", l.joinToString("\n")).apply()
    }

    // ---------- সুইচ ও সেটিং ----------
    fun enabled(c: Context) = p(c).getBoolean("on", true)
    fun setEnabled(c: Context, v: Boolean) { p(c).edit().putBoolean("on", v).apply() }

    fun txtOn(c: Context) = p(c).getBoolean("txt", true)
    fun setTxtOn(c: Context, v: Boolean) { p(c).edit().putBoolean("txt", v).apply() }

    // ছবি আর ভিডিও আলাদা সুইচ (দুটোই একই AI ইঞ্জিন ব্যবহার করে)
    fun photoOn(c: Context) = p(c).getBoolean("photo", true)
    fun setPhotoOn(c: Context, v: Boolean) { p(c).edit().putBoolean("photo", v).apply() }

    fun videoOn(c: Context) = p(c).getBoolean("video", true)
    fun setVideoOn(c: Context, v: Boolean) { p(c).edit().putBoolean("video", v).apply() }

    fun reelOn(c: Context) = p(c).getBoolean("reel", true)
    fun setReelOn(c: Context, v: Boolean) { p(c).edit().putBoolean("reel", v).apply() }

    // 0 = পুরো ব্লক, 1 = ব্লার
    fun style(c: Context) = p(c).getInt("style", 0)
    fun setStyle(c: Context, v: Int) { p(c).edit().putInt("style", v).apply() }

    // ছবির সংবেদনশীলতা: 0 কম, 1 মাঝারি, 2 বেশি
    fun sens(c: Context) = p(c).getInt("sens", 1)
    fun setSens(c: Context, v: Int) { p(c).edit().putInt("sens", v).apply() }

    fun threshold(c: Context): Float = when (sens(c)) {
        0 -> 0.85f
        2 -> 0.5f
        else -> 0.7f
    }

    // ---------- গণনা ও স্ট্রিক ----------
    fun streak(c: Context): Long {
        val pr = p(c)
        val base = pr.getString("last_block", null)
            ?: pr.getString("inst", null)
            ?: today().also { pr.edit().putString("inst", it).apply() }
        return ChronoUnit.DAYS.between(LocalDate.parse(base), LocalDate.now())
    }

    fun best(c: Context): Long = maxOf(p(c).getLong("best", 0L), streak(c))

    fun addBlock(c: Context) {
        val pr = p(c)
        val s = streak(c)
        val e = pr.edit()
        if (s > pr.getLong("best", 0L)) e.putLong("best", s)
        val k = "cnt_" + today()
        e.putInt(k, pr.getInt(k, 0) + 1).putString("last_block", today()).apply()
    }

    fun todayCount(c: Context) = p(c).getInt("cnt_" + today(), 0)

    // ---------- PIN ----------
    fun pin(c: Context): String? = p(c).getString("pin", null)
    fun setPin(c: Context, v: String?) {
        if (v == null) p(c).edit().remove("pin").apply() else p(c).edit().putString("pin", v).apply()
    }
}
