package com.gabbah.call2chat

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.core.app.NotificationManagerCompat

/**
 * Invisibile: apre l'app scelta col messaggio pronto e si chiude.
 * Con gli allegati i passaggi possono essere più di uno (foto, video, PDF):
 * dopo il primo, i successivi partono dalla barra/notifica di NextBar.
 */
class SendActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Passaggio successivo (video, PDF…) lanciato dalla barra o dalla notifica
        if (intent.getBooleanExtra(EXTRA_NEXT, false)) {
            NotificationManagerCompat.from(this).cancel(4711)
            val next = NextBar.pop()
            if (next != null) launch(next)
            if (NextBar.hasNext()) NextBar.show(this)
            finish()
            return
        }

        val number = intent.getStringExtra(EXTRA_NUMBER)
        if (number == null) { finish(); return }
        val prefs = Prefs(this)
        NotificationManagerCompat.from(this).cancel(number.hashCode())
        NextBar.clear(this)

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
        val steps = Channel.plan(this, channel, number, text)

        // Primo passaggio. Se la chat diretta non si apre: scelta del contatto con gli allegati;
        // solo come ultima spiaggia il testo da solo.
        val ok = steps.firstOrNull()?.let { launch(it) } == true ||
            Channel.textOnly(this, channel, number, text)?.let { Channel.start(this, it) } == true

        if (ok) {
            prefs.templates().getOrNull(idx)?.let { prefs.countTemplate(it.name) }
            if (!prefs.isPro) prefs.sentCount = prefs.sentCount + 1
            prefs.markSent(number)
            prefs.countSent()
            if (steps.size > 1) NextBar.offer(this, steps.drop(1))
        } else {
            Toast.makeText(this, R.string.no_app, Toast.LENGTH_LONG).show()
        }
        finish()
    }

    /** Apre un passaggio; se la chat diretta non si apre prova con la scelta del contatto */
    private fun launch(i: Intent): Boolean {
        val err = Channel.tryStart(this, i) ?: return true
        if (i.hasExtra(Intent.EXTRA_STREAM)) {
            Toast.makeText(this, getString(R.string.att_failed, err), Toast.LENGTH_LONG).show()
            return Channel.start(this, Channel.chooser(i))
        }
        return false
    }

    companion object {
        const val EXTRA_NUMBER = "number"
        const val EXTRA_TEMPLATE = "template"
        const val EXTRA_SIM = "sim"
        const val EXTRA_NEXT = "next"
    }
}
