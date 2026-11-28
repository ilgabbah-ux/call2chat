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

        val tpl = if (intent.hasExtra(EXTRA_TEMPLATE)) intent.getIntExtra(EXTRA_TEMPLATE, 0) else null
        val idx = tpl ?: prefs.autoIndex()
        val sim = intent.getIntExtra(EXTRA_SIM, -1)
        if (Channel.open(this, prefs.channelFor(sim), number, prefs.messageFor(idx))) {
            prefs.templates().getOrNull(idx)?.let { prefs.countTemplate(it.name) }
            if (!prefs.isPro) prefs.sentCount = prefs.sentCount + 1
            prefs.markSent(number)
            prefs.countSent()
        } else {
            Toast.makeText(this, R.string.no_app, Toast.LENGTH_LONG).show()
        }
        finish()
    }

    companion object {
        const val EXTRA_NUMBER = "number"
        const val EXTRA_TEMPLATE = "template"
        const val EXTRA_SIM = "sim"
    }
}
