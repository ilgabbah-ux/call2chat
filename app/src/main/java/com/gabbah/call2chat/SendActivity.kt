package com.gabbah.call2chat

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.core.app.NotificationManagerCompat

/**
 * Invisibile: apre l'app scelta col messaggio pronto e si chiude.
 * Con gli allegati i passaggi possono essere due (foto/video, poi PDF):
 * il secondo parte da solo quando si torna dal primo.
 */
class SendActivity : Activity() {

    private var steps = ArrayList<Intent>()
    private var launched = false
    private var away = false

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
        val channel = prefs.channelFor(sim)
        val text = prefs.messageFor(idx)
        steps = ArrayList(Channel.plan(this, channel, number, text))

        // Primo passaggio; se non parte (es. app che non accetta i file) ripiego sul solo testo
        val first = steps.removeFirstOrNull()
        val ok = first != null && Channel.start(this, first, newTask = false) ||
            Channel.textOnly(this, channel, number, text)?.let { steps.clear(); Channel.start(this, it, newTask = false) } == true

        if (ok) {
            prefs.templates().getOrNull(idx)?.let { prefs.countTemplate(it.name) }
            if (!prefs.isPro) prefs.sentCount = prefs.sentCount + 1
            prefs.markSent(number)
            prefs.countSent()
            launched = true
            if (steps.isEmpty()) finish()
        } else {
            Toast.makeText(this, R.string.no_app, Toast.LENGTH_LONG).show()
            finish()
        }
    }

    override fun onPause() {
        super.onPause()
        if (launched) away = true
    }

    override fun onResume() {
        super.onResume()
        // Tornati dal passaggio precedente: parte il successivo (es. il PDF)
        if (!launched || !away) return
        away = false
        val next = steps.removeFirstOrNull()
        if (next == null || !Channel.start(this, next, newTask = false)) finish()
    }

    companion object {
        const val EXTRA_NUMBER = "number"
        const val EXTRA_TEMPLATE = "template"
        const val EXTRA_SIM = "sim"
    }
}
