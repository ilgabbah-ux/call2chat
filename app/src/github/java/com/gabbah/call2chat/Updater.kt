package com.gabbah.call2chat

import android.app.Activity
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

/**
 * Versione GitHub: all'apertura controlla version.json sul repo.
 * Se c'è una versione più nuova la scarica e chiede ad Android di installarla.
 */
object Updater {
    private const val VERSION_URL =
        "https://raw.githubusercontent.com/ilgabbah-ux/call2chat/main/download/version.json"
    @Volatile private var running = false

    fun check(activity: Activity) {
        if (running) return
        running = true
        val app = activity.applicationContext
        thread {
            try {
                val info = JSONObject(get(VERSION_URL).decodeToString())
                val code = info.getInt("versionCode")
                if (code <= BuildConfig.VERSION_CODE) return@thread
                val name = info.optString("versionName", code.toString())
                val apkUrl = info.getString("apk")

                activity.runOnUiThread {
                    Toast.makeText(app, app.getString(R.string.update_downloading, name), Toast.LENGTH_LONG).show()
                }
                val bytes = get(apkUrl)

                activity.runOnUiThread {
                    if (!app.packageManager.canRequestPackageInstalls()) {
                        Toast.makeText(app, R.string.update_allow, Toast.LENGTH_LONG).show()
                        try {
                            activity.startActivity(
                                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${app.packageName}"))
                            )
                        } catch (e: Exception) { }
                    }
                }
                install(activity, bytes)
            } catch (e: Exception) {
                // niente rete o file non trovato: riprova alla prossima apertura
            } finally {
                running = false
            }
        }
    }

    private fun get(url: String): ByteArray {
        var u = URL(url)
        repeat(5) {
            val c = u.openConnection() as HttpURLConnection
            c.connectTimeout = 15000
            c.readTimeout = 60000
            c.instanceFollowRedirects = true
            c.setRequestProperty("Cache-Control", "no-cache")
            val code = c.responseCode
            if (code in 300..399) {
                u = URL(u, c.getHeaderField("Location")); c.disconnect(); return@repeat
            }
            if (code != 200) throw IllegalStateException("HTTP $code")
            return c.inputStream.use { it.readBytes() }
        }
        throw IllegalStateException("too many redirects")
    }

    private fun install(activity: Activity, apk: ByteArray) {
        val installer = activity.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        params.setAppPackageName(activity.packageName)
        val id = installer.createSession(params)
        installer.openSession(id).use { s ->
            s.openWrite("call2chat.apk", 0, apk.size.toLong()).use { it.write(apk); s.fsync(it) }
            val pi = PendingIntent.getBroadcast(
                activity, id, Intent(activity, InstallReceiver::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )
            s.commit(pi.intentSender)
        }
    }
}
