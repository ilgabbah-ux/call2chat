package com.gabbah.call2chat

import android.content.Context
import android.telephony.TelephonyManager
import com.google.i18n.phonenumbers.PhoneNumberUtil
import java.util.Locale

object Numbers {
    fun digits(s: String) = s.filter { it.isDigit() }

    /** Paese del telefono (SIM, poi rete, poi lingua), es. "IT" */
    fun region(ctx: Context): String {
        val tm = ctx.getSystemService(TelephonyManager::class.java)
        return listOf(tm?.simCountryIso, tm?.networkCountryIso, Locale.getDefault().country)
            .firstOrNull { !it.isNullOrBlank() }?.uppercase() ?: "IT"
    }

    /**
     * Numero in formato internazionale senza "+" (es. 393331234567), come lo vuole WhatsApp.
     * Null se non è un numero utilizzabile.
     */
    fun toWhatsApp(raw: String, region: String): String? {
        val clean = raw.trim()
        if (digits(clean).length < 5) return null
        return try {
            val util = PhoneNumberUtil.getInstance()
            val n = util.parse(clean, region)
            digits(util.format(n, PhoneNumberUtil.PhoneNumberFormat.E164))
        } catch (e: Exception) {
            val d = digits(clean)
            if (clean.startsWith("+") || clean.startsWith("00")) d.removePrefix("00") else null
        }
    }

    /** "+39 333 123 4567" per mostrarlo nelle notifiche */
    fun pretty(waNumber: String): String = try {
        val util = PhoneNumberUtil.getInstance()
        util.format(util.parse("+$waNumber", null), PhoneNumberUtil.PhoneNumberFormat.INTERNATIONAL)
    } catch (e: Exception) {
        "+$waNumber"
    }
}
