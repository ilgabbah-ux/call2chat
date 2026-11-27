package com.gabbah.call2chat

import android.Manifest
import android.app.PendingIntent
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import java.net.URLEncoder

object Core {

    /** Chiamata da un numero non in rubrica: controlli e notifica. */
    fun onUnknownCall(ctx: Context, raw: String) {
        val prefs = Prefs(ctx)
        if (!prefs.enabled) return
        if (!prefs.isWithinHours()) return
        val number = Numbers.toWhatsApp(raw, Numbers.region(ctx)) ?: return
        if (prefs.isExcluded(number)) return

        // La scheda con "Invia" compare sempre: l'invio lo decide la persona
        Overlay.show(ctx, number)

        // La notifica invece rispetta la pausa per lo stesso numero
        if (prefs.recentlySeen(number)) return
        prefs.addCall(number)
        notify(ctx, number)
    }

    fun notify(ctx: Context, number: String) {
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) return

        val prefs = Prefs(ctx)
        val pretty = Numbers.pretty(number)
        val pi = sendIntent(ctx, number)

        val b = NotificationCompat.Builder(ctx, App.CHANNEL_CALLS)
            .setSmallIcon(R.drawable.ic_notify)
            .setContentTitle(ctx.getString(R.string.notif_title, pretty))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setAutoCancel(true)
            .setContentIntent(pi)

        if (prefs.canSend) {
            b.setContentText(ctx.getString(R.string.notif_text))
                .setStyle(NotificationCompat.BigTextStyle().bigText(prefs.message))
                .addAction(R.drawable.ic_send, ctx.getString(R.string.notif_action), pi)
        } else {
            b.setContentText(ctx.getString(R.string.notif_limit))
        }

        NotificationManagerCompat.from(ctx).notify(number.hashCode(), b.build())
    }

    fun sendIntent(ctx: Context, number: String): PendingIntent {
        val i = Intent(ctx, SendActivity::class.java)
            .putExtra(SendActivity.EXTRA_NUMBER, number)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return PendingIntent.getActivity(
            ctx, number.hashCode(), i,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun isInstalled(ctx: Context, pkg: String): Boolean = try {
        ctx.packageManager.getPackageInfo(pkg, 0); true
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }

    /** Apre la chat col messaggio già scritto. True se è riuscito. */
    fun openChat(ctx: Context, number: String, text: String): Boolean {
        val uri = Uri.parse(
            "https://api.whatsapp.com/send?phone=$number&text=" + URLEncoder.encode(text, "UTF-8")
        )
        val chosen = Prefs(ctx).targetPackage
        val other = if (chosen == Prefs.WA) Prefs.WA_BUSINESS else Prefs.WA
        val order = listOf(chosen, other).filter { isInstalled(ctx, it) } + listOf<String?>(null)
        for (pkg in order) {
            try {
                val i = Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (pkg != null) i.setPackage(pkg)
                ctx.startActivity(i)
                return true
            } catch (e: ActivityNotFoundException) {
                // provo il successivo
            }
        }
        return false
    }
}
