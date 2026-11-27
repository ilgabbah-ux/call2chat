package com.gabbah.call2chat

import android.os.Build
import android.telecom.Call
import android.telecom.CallScreeningService
import android.telecom.TelecomManager

/**
 * Android chiama questo servizio solo per le chiamate da numeri che NON sono in rubrica.
 * Non blocca mai la chiamata: la lascia squillare e prepara la notifica col messaggio.
 */
class ScreeningService : CallScreeningService() {

    override fun onScreenCall(details: Call.Details) {
        // Prima di tutto lascio passare la chiamata, senza toccarla
        respondToCall(details, CallResponse.Builder().build())

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            details.callDirection != Call.Details.DIRECTION_INCOMING
        ) return
        if (details.handlePresentation != TelecomManager.PRESENTATION_ALLOWED) return
        val raw = details.handle?.schemeSpecificPart ?: return

        Core.onUnknownCall(this, raw)
    }
}
