package com.gabbah.call2chat

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

object Core {

    /** Chiamata da un numero non in rubrica: controlli e notifica. */
    fun onUnknownCall(ctx: Context, raw: String, sim: Int = -1) {
        val prefs = Prefs(ctx)
        if (!prefs.enabled) return
        if (sim >= 0 && !prefs.simEnabled(sim)) return
        if (!prefs.isWithinHours()) return
        val number = Numbers.toWhatsApp(raw, Numbers.region(ctx)) ?: return
        if (prefs.isExcluded(number)) return
        prefs.countCall()

        // La scheda con "Invia" compare sempre: l'invio lo decide la persona
        Overlay.show(ctx, number, sim)

        // Ogni chiamata va nello storico; la notifica invece rispetta la pausa
        val seen = prefs.recentlySeen(number)
        prefs.addCall(number, sim)
        if (!seen) { prefs.markNotified(number); notify(ctx, number, sim) }
    }

    fun notify(ctx: Context, number: String, sim: Int = -1) {
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) return

        val prefs = Prefs(ctx)
        val pretty = Numbers.pretty(number)
        val pi = sendIntent(ctx, number, null, sim)

        val b = NotificationCompat.Builder(ctx, App.CHANNEL_CALLS)
            .setSmallIcon(R.drawable.ic_notify)
            .setContentTitle(ctx.getString(R.string.notif_title, pretty))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setAutoCancel(true)
            .setContentIntent(pi)

        if (prefs.canSend) {
            b.setContentText(ctx.getString(R.string.notif_text, ctx.getString(Channel.resolve(ctx, prefs.channelFor(sim)).label)))
                .setStyle(NotificationCompat.BigTextStyle().bigText(prefs.message))
                .addAction(R.drawable.ic_send, ctx.getString(R.string.notif_action), pi)
            // Altri messaggi pronti come pulsanti (al massimo due)
            val auto = prefs.autoIndex()
            prefs.templates().withIndex().filter { it.index != auto }.take(2).forEach { (i, t) ->
                b.addAction(R.drawable.ic_send, t.name, sendIntent(ctx, number, i, sim))
            }
        } else {
            b.setContentText(ctx.getString(R.string.notif_limit))
        }

        NotificationManagerCompat.from(ctx).notify(number.hashCode(), b.build())
    }

    fun sendIntent(ctx: Context, number: String, template: Int? = null, sim: Int = -1): PendingIntent {
        val i = Intent(ctx, SendActivity::class.java)
            .putExtra(SendActivity.EXTRA_NUMBER, number)
            .putExtra(SendActivity.EXTRA_SIM, sim)
        if (template != null) i.putExtra(SendActivity.EXTRA_TEMPLATE, template)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return PendingIntent.getActivity(
            ctx, number.hashCode() * 31 + (template ?: -1), i,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun isInstalled(ctx: Context, pkg: String): Boolean = try {
        ctx.packageManager.getPackageInfo(pkg, 0); true
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }

}
