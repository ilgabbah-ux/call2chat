package com.gabbah.call2chat

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/** Un messaggio pronto: nome breve (per la scheda) e testo. */
data class Template(val name: String, val text: String)

object Vars {
    private val re = Regex("""\{(ora|time|giorno|day|data|date|domani|tomorrow)(\+(\d{1,2}))?\}""", RegexOption.IGNORE_CASE)

    /** Sostituisce {ora}, {ora+1}, {giorno}, {data}, {domani} (anche in inglese: {time}, {day}, {date}, {tomorrow}) */
    fun render(text: String, now: Calendar = Calendar.getInstance(), locale: Locale = Locale.getDefault()): String =
        re.replace(text) { m ->
            val key = m.groupValues[1].lowercase()
            val plus = m.groupValues[3].toIntOrNull() ?: 0
            val c = now.clone() as Calendar
            when (key) {
                "ora", "time" -> {
                    c.add(Calendar.HOUR_OF_DAY, plus)
                    SimpleDateFormat("HH:mm", locale).format(c.time)
                }
                "giorno", "day" -> {
                    c.add(Calendar.DAY_OF_YEAR, plus)
                    SimpleDateFormat("EEEE", locale).format(c.time)
                }
                "domani", "tomorrow" -> {
                    c.add(Calendar.DAY_OF_YEAR, 1 + plus)
                    SimpleDateFormat("EEEE", locale).format(c.time)
                }
                else -> {
                    c.add(Calendar.DAY_OF_YEAR, plus)
                    SimpleDateFormat("dd/MM", locale).format(c.time)
                }
            }
        }
}

/** Orario di lavoro: fuori da questo orario si usa il messaggio "fuori orario". */
data class Schedule(
    val enabled: Boolean,
    val start: Int,          // minuti dalla mezzanotte
    val end: Int,
    val weekendOff: Boolean,
    val offIndex: Int        // messaggio da usare fuori orario
) {
    fun isOffHours(now: Calendar): Boolean {
        if (!enabled) return false
        val dow = now.get(Calendar.DAY_OF_WEEK)
        if (weekendOff && (dow == Calendar.SATURDAY || dow == Calendar.SUNDAY)) return true
        val m = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
        if (start == end) return false
        val inside = if (start < end) m in start until end else (m >= start || m < end)
        return !inside
    }
}
