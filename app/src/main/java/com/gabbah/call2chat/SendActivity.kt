package com.gabbah.call2chat

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.core.app.NotificationManagerCompat

/** Invisibile: aperta dalla notifica, apre WhatsApp col messaggio pronto e si chiude. */
class SendActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val number = intent.getStringExtra(EXTRA_NUMBER)
        if (number == null) { finish(); return }
        val prefs = Prefs(this)
        NotificationManagerCompat.from(this).cancel(number.hashCode())

        if (!prefs.canSend) {
            startActivity(
                Intent(this, MainActivity::class.java)
                    .putExtra(MainActivity.EXTRA_PAYWALL, true)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            )
            finish()
            return
        }

        if (Core.openChat(this, number, prefs.message)) {
            if (!prefs.isPro) prefs.sentCount = prefs.sentCount + 1
            prefs.markSent(number)
        } else {
            Toast.makeText(this, R.string.no_whatsapp, Toast.LENGTH_LONG).show()
        }
        finish()
    }

    companion object {
        const val EXTRA_NUMBER = "number"
    }
}
