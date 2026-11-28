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

        /** Apre il canale con il messaggio pronto. True se è riuscito. */
        fun open(ctx: Context, chosen: Channel, number: String, text: String, noFiles: Boolean = false): Boolean {
            val c = resolve(ctx, chosen)
            val enc = URLEncoder.encode(text, "UTF-8")
            fun start(i: Intent): Boolean = try {
                ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true
            } catch (e: ActivityNotFoundException) { false } catch (e: SecurityException) { false }

            val files = Attachments.uris(ctx)
            if (!noFiles && files.isNotEmpty()) return openWithFiles(ctx, c, number, text, files, ::start)

            return when (c) {
                WHATSAPP, WA_BUSINESS -> {
                    val uri = Uri.parse("https://api.whatsapp.com/send?phone=$number&text=$enc")
                    val other = if (c == WHATSAPP) WA_BUSINESS else WHATSAPP
                    listOf(c, other).filter { isInstalled(ctx, it) }
                        .any { start(Intent(Intent.ACTION_VIEW, uri).setPackage(it.pkg)) } ||
                        start(Intent(Intent.ACTION_VIEW, uri))
                }
                SMS -> start(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:+$number")).putExtra("sms_body", text))
                TELEGRAM -> {
                    // Telegram non apre una chat da numero con testo: si condivide e si sceglie la chat
                    start(Intent(Intent.ACTION_VIEW, Uri.parse("tg://msg?text=$enc")).setPackage(c.pkg)) ||
                        start(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text).setPackage(c.pkg))
                }
                WECHAT -> {
                    // WeChat non permette di pre-scrivere: copio il testo e apro l'app
                    copy(ctx, text)
                    Toast.makeText(ctx, ctx.getString(R.string.wechat_copied, Numbers.pretty(number)), Toast.LENGTH_LONG).show()
                    val launch = ctx.packageManager.getLaunchIntentForPackage(c.pkg!!)
                    launch != null && start(launch)
                }
                AUTO -> false
            }
        }

        /**
         * Con allegati. WhatsApp: apre direttamente la chat del numero (parametro "jid", non ufficiale
         * ma usato da molte app business) con foto/video/PDF e il testo come didascalia.
         * Se non riesce, apre la chat col testo e poi la condivisione degli allegati.
         */
        private fun openWithFiles(ctx: Context, c: Channel, number: String, text: String,
                                  files: ArrayList<Uri>, start: (Intent) -> Boolean): Boolean {
            fun share(pkg: String?, extra: (Intent) -> Unit = {}): Intent {
                val i = if (files.size == 1) Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, files[0])
                else Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, files)
                i.type = "*/*"
                i.putExtra(Intent.EXTRA_TEXT, text)
                if (pkg != null) i.setPackage(pkg)
                val clip = ClipData.newRawUri("", files[0])
                files.drop(1).forEach { clip.addItem(ClipData.Item(it)) }
                i.clipData = clip
                i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                extra(i)
                return i
            }
            return when (c) {
                WHATSAPP, WA_BUSINESS -> {
                    start(share(c.pkg) { it.putExtra("jid", "$number@s.whatsapp.net") }) || run {
                        val ok = open(ctx, c, number, text, noFiles = true)
                        start(Intent.createChooser(share(c.pkg), null)); ok
                    }
                }
                SMS -> start(share(null) { it.putExtra("address", "+$number").putExtra("sms_body", text) }) ||
                    open(ctx, SMS, number, text, noFiles = true)
                WECHAT -> {
                    copy(ctx, text)
                    Toast.makeText(ctx, ctx.getString(R.string.wechat_copied, Numbers.pretty(number)), Toast.LENGTH_LONG).show()
                    start(share(c.pkg))
                }
                else -> start(share(c.pkg))
            }
        }

        /** Anteprima senza destinatario, per vedere il testo nell'app scelta */
        fun preview(ctx: Context, chosen: Channel, text: String): Boolean {
            val c = resolve(ctx, chosen)
            val i = when (c) {
                SMS -> Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:")).putExtra("sms_body", text)
                WECHAT -> { copy(ctx, text); ctx.packageManager.getLaunchIntentForPackage(c.pkg!!) ?: return false }
                else -> Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text).setPackage(c.pkg)
            }
            return try { ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true } catch (e: Exception) { false }
        }

        private fun copy(ctx: Context, text: String) {
            ctx.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("Call2Chat", text))
        }
    }
}
