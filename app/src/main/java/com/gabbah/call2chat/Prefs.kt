package com.gabbah.call2chat

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

/** Tutte le impostazioni e i contatori, salvati solo sul telefono. */
class Prefs(ctx: Context) {
    private val app = ctx.applicationContext
    private val p: SharedPreferences = app.getSharedPreferences("call2chat", Context.MODE_PRIVATE)

    var enabled: Boolean
        get() = p.getBoolean("enabled", true)
        set(v) = p.edit().putBoolean("enabled", v).apply()

    var message: String
        get() = p.getString("message", null) ?: app.getString(R.string.default_message)
        set(v) = p.edit().putString("message", v).apply()

    /** "com.whatsapp" oppure "com.whatsapp.w4b" */
    var targetPackage: String
        get() = p.getString("target", WA) ?: WA
        set(v) = p.edit().putString("target", v).apply()

    /** Ore prima di riproporre lo stesso numero (0 = sempre) */
    var cooldownHours: Int
        get() = p.getInt("cooldown", 24)
        set(v) = p.edit().putInt("cooldown", v.coerceIn(0, 720)).apply()

    var hoursEnabled: Boolean
        get() = p.getBoolean("hours_on", false)
        set(v) = p.edit().putBoolean("hours_on", v).apply()

    /** Minuti dalla mezzanotte */
    var hoursStart: Int
        get() = p.getInt("hours_start", 8 * 60)
        set(v) = p.edit().putInt("hours_start", v).apply()

    var hoursEnd: Int
        get() = p.getInt("hours_end", 20 * 60)
        set(v) = p.edit().putInt("hours_end", v).apply()

    /** Numeri esclusi, uno per riga */
    var excluded: String
        get() = p.getString("excluded", "") ?: ""
        set(v) = p.edit().putString("excluded", v).apply()

    var sentCount: Int
        get() = p.getInt("sent", 0)
        set(v) = p.edit().putInt("sent", v).apply()

    var isPro: Boolean
        get() = p.getBoolean("pro", false)
        set(v) = p.edit().putBoolean("pro", v).apply()

    val freeLeft: Int get() = (FREE_LIMIT - sentCount).coerceAtLeast(0)
    val canSend: Boolean get() = isPro || sentCount < FREE_LIMIT

    fun isWithinHours(now: Calendar = Calendar.getInstance()): Boolean {
        if (!hoursEnabled) return true
        val m = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
        val s = hoursStart
        val e = hoursEnd
        return if (s == e) true else if (s < e) m in s until e else (m >= s || m < e)
    }

    fun isExcluded(e164Digits: String): Boolean =
        excluded.lines().map { Numbers.digits(it) }.filter { it.length >= 5 }
            .any { e164Digits.endsWith(it) || it.endsWith(e164Digits) }

    // ---- Storico delle ultime chiamate (max 30) ----

    data class Entry(val number: String, val time: Long, val sent: Boolean)

    fun history(): List<Entry> {
        val arr = JSONArray(p.getString("history", "[]"))
        return (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            Entry(o.getString("n"), o.getLong("t"), o.optBoolean("s"))
        }
    }

    private fun saveHistory(list: List<Entry>) {
        val arr = JSONArray()
        list.take(30).forEach {
            arr.put(JSONObject().put("n", it.number).put("t", it.time).put("s", it.sent))
        }
        p.edit().putString("history", arr.toString()).apply()
    }

    /** true se il numero è già stato proposto entro le ore di pausa */
    /** true se a quel numero è già stata proposta la notifica entro le ore di pausa */
    fun recentlySeen(number: String, now: Long = System.currentTimeMillis()): Boolean {
        val h = cooldownHours
        if (h == 0) return false
        val last = p.getLong("notified_$number", 0L)
        return last > 0 && now - last < h * 3_600_000L
    }

    fun markNotified(number: String) =
        p.edit().putLong("notified_$number", System.currentTimeMillis()).apply()

    fun registerListener(l: SharedPreferences.OnSharedPreferenceChangeListener) =
        p.registerOnSharedPreferenceChangeListener(l)

    fun unregisterListener(l: SharedPreferences.OnSharedPreferenceChangeListener) =
        p.unregisterOnSharedPreferenceChangeListener(l)

    fun addCall(number: String) {
        saveHistory(listOf(Entry(number, System.currentTimeMillis(), false)) + history())
    }

    fun markSent(number: String) {
        var done = false
        saveHistory(history().map {
            if (!done && it.number == number) { done = true; it.copy(sent = true) } else it
        })
    }

    companion object {
        const val FREE_LIMIT = 100
        const val WA = "com.whatsapp"
        const val WA_BUSINESS = "com.whatsapp.w4b"
    }
}
