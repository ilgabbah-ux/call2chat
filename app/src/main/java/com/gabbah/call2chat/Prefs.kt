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

    // ---- Messaggi pronti ----

    fun templates(): List<Template> {
        val raw = p.getString("templates", null)
        if (raw == null) {
            // Prima volta: il messaggio di prima (se c'era) + tre esempi
            val first = Template(app.getString(R.string.tpl_default_name),
                p.getString("message", null) ?: app.getString(R.string.default_message))
            val list = listOf(
                first,
                Template(app.getString(R.string.tpl_meeting_name), app.getString(R.string.tpl_meeting_text)),
                Template(app.getString(R.string.tpl_ride_name), app.getString(R.string.tpl_ride_text)),
                Template(app.getString(R.string.tpl_closed_name), app.getString(R.string.tpl_closed_text))
            )
            saveTemplates(list)
            return list
        }
        val arr = JSONArray(raw)
        return (0 until arr.length()).map {
            val o = arr.getJSONObject(it); Template(o.optString("n"), o.optString("t"))
        }.ifEmpty { listOf(Template(app.getString(R.string.tpl_default_name), app.getString(R.string.default_message))) }
    }

    fun saveTemplates(list: List<Template>) {
        val arr = JSONArray()
        list.forEach { arr.put(JSONObject().put("n", it.name).put("t", it.text)) }
        p.edit().putString("templates", arr.toString()).apply()
        defaultIndex = defaultIndex.coerceIn(0, list.size - 1)
        offIndex = offIndex.coerceIn(0, list.size - 1)
    }

    /** Messaggio predefinito (orario normale) */
    var defaultIndex: Int
        get() = p.getInt("tpl_default", 0)
        set(v) = p.edit().putInt("tpl_default", v).apply()

    var offEnabled: Boolean
        get() = p.getBoolean("off_on", false)
        set(v) = p.edit().putBoolean("off_on", v).apply()

    var workStart: Int
        get() = p.getInt("work_start", 9 * 60)
        set(v) = p.edit().putInt("work_start", v).apply()

    var workEnd: Int
        get() = p.getInt("work_end", 19 * 60)
        set(v) = p.edit().putInt("work_end", v).apply()

    var weekendOff: Boolean
        get() = p.getBoolean("weekend_off", true)
        set(v) = p.edit().putBoolean("weekend_off", v).apply()

    /** Messaggio fuori orario (di base "Fuori orario") */
    var offIndex: Int
        get() = p.getInt("tpl_off", 3)
        set(v) = p.edit().putInt("tpl_off", v).apply()

    fun schedule() = Schedule(offEnabled, workStart, workEnd, weekendOff, offIndex)

    /** Indice del messaggio da usare adesso, secondo l'orario */
    fun autoIndex(now: Calendar = Calendar.getInstance()): Int {
        val n = templates().size
        return (if (schedule().isOffHours(now)) offIndex else defaultIndex).coerceIn(0, n - 1)
    }

    /** Testo pronto da inviare (con le variabili già sostituite) */
    fun messageFor(index: Int? = null, now: Calendar = Calendar.getInstance()): String {
        val list = templates()
        val i = (index ?: autoIndex(now)).coerceIn(0, list.size - 1)
        return Vars.render(list[i].text, now)
    }

    /** Il testo del messaggio automatico di adesso */
    val message: String get() = messageFor()

    // ---- Passaggi confermati a mano nella configurazione ----

    fun isConfirmed(step: String) = p.getBoolean("ok_$step", false)
    fun setConfirmed(step: String, v: Boolean) = p.edit().putBoolean("ok_$step", v).apply()

    var setupSeen: Boolean
        get() = p.getBoolean("setup_seen", false)
        set(v) = p.edit().putBoolean("setup_seen", v).apply()

    // ---- Statistiche ----

    data class Day(val label: String, val calls: Int, val sent: Int)
    data class Stats(val calls7: Int, val sent7: Int, val callsAll: Int, val sentAll: Int, val days: List<Day>,
                     val callsPrev7: Int, val sentPrev7: Int, val topTemplate: String?, val topCount: Int)

    fun countTemplate(name: String) {
        val o = JSONObject(p.getString("tpl_used", "{}"))
        o.put(name, o.optInt(name) + 1)
        p.edit().putString("tpl_used", o.toString()).apply()
    }

    private fun dayKey(c: Calendar) = "%04d%02d%02d".format(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH))

    private fun bump(field: String, now: Calendar) {
        val o = JSONObject(p.getString("stats", "{}"))
        val k = dayKey(now)
        val d = o.optJSONObject(k) ?: JSONObject()
        d.put(field, d.optInt(field) + 1)
        o.put(k, d)
        // tengo solo gli ultimi 60 giorni
        val keys = o.keys().asSequence().toList().sorted()
        keys.dropLast(60).forEach { o.remove(it) }
        p.edit().putString("stats", o.toString())
            .putInt("stat_$field", p.getInt("stat_$field", 0) + 1).apply()
    }

    fun countCall(now: Calendar = Calendar.getInstance()) = bump("c", now)
    fun countSent(now: Calendar = Calendar.getInstance()) = bump("s", now)

    fun stats(now: Calendar = Calendar.getInstance()): Stats {
        val o = JSONObject(p.getString("stats", "{}"))
        fun day(back: Int): Day {
            val c = now.clone() as Calendar
            c.add(Calendar.DAY_OF_YEAR, -back)
            val d = o.optJSONObject(dayKey(c))
            return Day(java.text.SimpleDateFormat("EEE", java.util.Locale.getDefault()).format(c.time),
                d?.optInt("c") ?: 0, d?.optInt("s") ?: 0)
        }
        val days = (6 downTo 0).map { day(it) }
        val prev = (13 downTo 7).map { day(it) }
        val used = JSONObject(p.getString("tpl_used", "{}"))
        val top = used.keys().asSequence().maxByOrNull { used.optInt(it) }
        return Stats(
            calls7 = days.sumOf { it.calls }, sent7 = days.sumOf { it.sent },
            callsAll = p.getInt("stat_c", 0), sentAll = p.getInt("stat_s", 0),
            days = days, callsPrev7 = prev.sumOf { it.calls }, sentPrev7 = prev.sumOf { it.sent },
            topTemplate = top, topCount = top?.let { used.optInt(it) } ?: 0
        )
    }

    /** Canale scelto (prima della 1.8 c'era solo WhatsApp / Business) */
    var channel: Channel
        get() = p.getString("channel", null)?.let { Channel.of(it) }
            ?: if (p.getString("target", WA) == WA_BUSINESS) Channel.WA_BUSINESS else Channel.AUTO
        set(v) = p.edit().putString("channel", v.id).apply()

    // ---- Doppia SIM: ogni SIM attiva/spenta e con il suo canale ----

    fun simEnabled(sub: Int) = p.getBoolean("sim_on_$sub", true)
    fun setSimEnabled(sub: Int, v: Boolean) = p.edit().putBoolean("sim_on_$sub", v).apply()
    fun simChannel(sub: Int): Channel? = p.getString("sim_ch_$sub", null)?.let { Channel.of(it) }
    fun setSimChannel(sub: Int, c: Channel) = p.edit().putString("sim_ch_$sub", c.id).apply()

    /** Canale per la chiamata arrivata su quella SIM (o quello generale) */
    fun channelFor(sim: Int?): Channel = sim?.takeIf { it >= 0 }?.let { simChannel(it) } ?: channel

    /** Tema: "dark" (predefinito), "light", "auto" (come il telefono) */
    var theme: String
        get() = p.getString("theme", "dark") ?: "dark"
        set(v) = p.edit().putString("theme", v).apply()

    var lastTab: Int
        get() = p.getInt("tab", 0)
        set(v) = p.edit().putInt("tab", v).apply()

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

    data class Entry(val number: String, val time: Long, val sent: Boolean, val sim: Int = -1)

    fun history(): List<Entry> {
        val arr = JSONArray(p.getString("history", "[]"))
        return (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            Entry(o.getString("n"), o.getLong("t"), o.optBoolean("s"), o.optInt("m", -1))
        }
    }

    private fun saveHistory(list: List<Entry>) {
        val arr = JSONArray()
        list.take(30).forEach {
            arr.put(JSONObject().put("n", it.number).put("t", it.time).put("s", it.sent).put("m", it.sim))
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

    fun addCall(number: String, sim: Int = -1) {
        saveHistory(listOf(Entry(number, System.currentTimeMillis(), false, sim)) + history())
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
