package com.gabbah.call2chat

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import java.net.URLEncoder

/** Dove parte il messaggio. AUTO = WhatsApp, poi WhatsApp Business, altrimenti SMS. */
enum class Channel(val id: String, val pkg: String?, val label: Int, val sendLabel: Int) {
    AUTO("auto", null, R.string.ch_auto, R.string.send_whatsapp),
    WHATSAPP("wa", "com.whatsapp", R.string.ch_whatsapp, R.string.send_whatsapp),
    WA_BUSINESS("wab", "com.whatsapp.w4b", R.string.ch_wa_business, R.string.send_whatsapp),
    SMS("sms", null, R.string.ch_sms, R.string.send_sms),
    TELEGRAM("tg", "org.telegram.messenger", R.string.ch_telegram, R.string.send_telegram),
    WECHAT("wechat", "com.tencent.mm", R.string.ch_wechat, R.string.send_wechat);

    companion object {
        fun of(id: String?) = entries.firstOrNull { it.id == id } ?: AUTO

        fun isInstalled(ctx: Context, c: Channel): Boolean =
            c == AUTO || c == SMS || (c.pkg != null && Core.isInstalled(ctx, c.pkg))

        /** Il canale vero da usare adesso */
        fun resolve(ctx: Context, c: Channel): Channel = when {
            c != AUTO -> c
            Core.isInstalled(ctx, WHATSAPP.pkg!!) -> WHATSAPP
            Core.isInstalled(ctx, WA_BUSINESS.pkg!!) -> WA_BUSINESS
            else -> SMS
        }

        /**
         * I passaggi per inviare: uno o più Intent da aprire uno dopo l'altro.
         * number = null → anteprima senza destinatario.
         *
         * WhatsApp con allegati: si apre direttamente la chat del numero (parametro "jid", non ufficiale
         * ma usato da molte app business). Foto e video partono insieme con il testo come didascalia;
         * il PDF parte in un secondo passaggio, perché WhatsApp scarta i documenti mescolati alle foto.
         */
        fun plan(ctx: Context, chosen: Channel, number: String?, text: String): List<Intent> {
            val c = resolve(ctx, chosen)
            val enc = URLEncoder.encode(text, "UTF-8")
            val media = Attachments.uris(ctx, Attachments.Kind.PHOTO, Attachments.Kind.VIDEO)
            val docs = Attachments.uris(ctx, Attachments.Kind.PDF)
            val all = ArrayList(media + docs)
            val jid = number?.let { "$it@s.whatsapp.net" }

            fun share(files: ArrayList<Uri>, type: String, caption: String?, pkg: String?, extra: (Intent) -> Unit = {}): Intent {
                val i = if (files.size == 1) Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, files[0])
                else Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, files)
                i.type = type
                if (caption != null) i.putExtra(Intent.EXTRA_TEXT, caption)
                if (pkg != null) i.setPackage(pkg)
                val clip = ClipData.newRawUri("", files[0])
                files.drop(1).forEach { clip.addItem(ClipData.Item(it)) }
                i.clipData = clip
                i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                extra(i)
                return i
            }
            fun mediaType() = when {
                media.isEmpty() -> "*/*"
                Attachments.count(ctx, Attachments.Kind.VIDEO) == 0 -> "image/*"
                Attachments.count(ctx, Attachments.Kind.PHOTO) == 0 -> "video/*"
                else -> "*/*"
            }

            return when (c) {
                WHATSAPP, WA_BUSINESS -> {
                    if (all.isEmpty()) {
                        if (number == null) listOf(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text).setPackage(c.pkg))
                        else listOf(Intent(Intent.ACTION_VIEW, Uri.parse("https://api.whatsapp.com/send?phone=$number&text=$enc")).setPackage(c.pkg))
                    } else buildList {
                        // Un passaggio per tipo: WhatsApp accetta meglio foto, video e PDF separati.
                        // Il testo va come didascalia del primo.
                        var caption: String? = text
                        listOf(Attachments.Kind.PHOTO to "image/*", Attachments.Kind.VIDEO to "video/*", Attachments.Kind.PDF to "application/pdf")
                            .forEach { (k, type) ->
                                val f = Attachments.uris(ctx, k)
                                if (f.isNotEmpty()) {
                                    add(share(f, type, caption, c.pkg) { if (jid != null) it.putExtra("jid", jid) })
                                    caption = null
                                }
                            }
                    }
                }
                SMS -> {
                    if (media.isEmpty()) listOf(Intent(Intent.ACTION_SENDTO, Uri.parse(if (number != null) "smsto:+$number" else "smsto:")).putExtra("sms_body", text))
                    else listOf(share(media, mediaType(), text, null) {
                        it.putExtra("sms_body", text); if (number != null) it.putExtra("address", "+$number")
                    })
                }
                TELEGRAM -> {
                    // Telegram non apre una chat da numero: si condivide e si sceglie la chat
                    if (all.isEmpty()) listOf(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text).setPackage(c.pkg))
                    else listOf(share(all, "*/*", text, c.pkg))
                }
                WECHAT -> {
                    // WeChat non permette di pre-scrivere: copio il testo e apro l'app (o la condivisione dei file)
                    copy(ctx, text)
                    if (number != null) Toast.makeText(ctx, ctx.getString(R.string.wechat_copied, Numbers.pretty(number)), Toast.LENGTH_LONG).show()
                    if (all.isEmpty()) listOfNotNull(ctx.packageManager.getLaunchIntentForPackage(c.pkg!!))
                    else listOf(share(all, "*/*", null, c.pkg))
                }
                AUTO -> emptyList()
            }
        }

        /** Apre il primo passaggio (usato dove non serve la sequenza). True se è riuscito. */
        fun open(ctx: Context, chosen: Channel, number: String, text: String): Boolean =
            plan(ctx, chosen, number, text).firstOrNull()?.let { start(ctx, it) } ?: false

        /** Anteprima senza destinatario, allegati compresi */
        fun preview(ctx: Context, chosen: Channel, text: String): Boolean =
            plan(ctx, chosen, null, text).firstOrNull()?.let { start(ctx, it) } ?: false

        fun start(ctx: Context, i: Intent, newTask: Boolean = true): Boolean = tryStart(ctx, i, newTask) == null

        /** Apre l'Intent; null se è partito, altrimenti il motivo dell'errore */
        fun tryStart(ctx: Context, i: Intent, newTask: Boolean = true): String? = try {
            if (newTask) i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ctx.startActivity(i); null
        } catch (e: Exception) { e.javaClass.simpleName + (e.message?.let { ": " + it.take(120) } ?: "") }

        /** Stesso invio ma con la scelta del contatto (se la chat diretta non si apre) */
        fun chooser(i: Intent): Intent = Intent.createChooser(Intent(i).apply { removeExtra("jid") }, null)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

        /** Solo testo, quando l'invio con allegati non parte */
        fun textOnly(ctx: Context, chosen: Channel, number: String, text: String): Intent? {
            val c = resolve(ctx, chosen)
            val enc = URLEncoder.encode(text, "UTF-8")
            return when (c) {
                WHATSAPP, WA_BUSINESS -> Intent(Intent.ACTION_VIEW, Uri.parse("https://api.whatsapp.com/send?phone=$number&text=$enc")).setPackage(c.pkg)
                SMS -> Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:+$number")).putExtra("sms_body", text)
                else -> null
            }
        }

        private fun copy(ctx: Context, text: String) {
            ctx.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("Call2Chat", text))
        }
    }
}
