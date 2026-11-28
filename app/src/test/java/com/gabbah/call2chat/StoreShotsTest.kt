package com.gabbah.call2chat

import android.app.Activity
import android.app.Application
import android.app.role.RoleManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.View
import android.view.WindowManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowSettings
import org.robolectric.shadows.ShadowWindowManagerImpl
import java.io.File

/**
 * Screenshot per la scheda del Play Store (gira solo se esiste la cartella /home/claude/shots).
 * ./gradlew testGithubDebugUnitTest --tests '*StoreShotsTest*' -Dshots=/percorso
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-notnight-xxhdpi")
class StoreShotsTest {

    private val out: String? get() = "/home/claude/shots".takeIf { File(it).isDirectory }

    private fun save(v: View, name: String, w: Int, h: Int) {
        v.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
            if (h > 0) View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY)
            else View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        v.layout(0, 0, v.measuredWidth, v.measuredHeight)
        val bmp = Bitmap.createBitmap(v.measuredWidth, v.measuredHeight, Bitmap.Config.ARGB_8888)
        v.draw(Canvas(bmp))
        File(out, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun setup(lang: String): Application {
        val app = ApplicationProvider.getApplicationContext<Application>()
        ShadowSettings.setCanDrawOverlays(true)
        shadowOf(app.getSystemService(RoleManager::class.java)).addHeldRole(RoleManager.ROLE_CALL_SCREENING)
        shadowOf(app).grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS, android.Manifest.permission.READ_PHONE_STATE)
        shadowOf(app.packageManager).installPackage(android.content.pm.PackageInfo().apply { packageName = "com.whatsapp" })
        val p = Prefs(app)
        p.setupSeen = true
        (shadowOf(app.packageManager) as org.robolectric.shadows.ShadowApplicationPackageManager).setAutoRevokeWhitelisted(true)
        Health.Step.values().forEach { st -> p.setConfirmed(st.name, true) }
        p.theme = "dark"
        val itL = lang == "it"
        p.saveTemplates(listOf(
            Template(if (itL) "Standard" else "Standard",
                if (itL) "Ciao! Ho visto la tua chiamata, ora non posso rispondere.\nTi mando listino e foto qui sotto, ti richiamo entro le {ora+1}."
                else "Hi! I saw your call but can't answer right now.\nHere are our prices and photos, I'll call you back by {time+1}."),
            Template(if (itL) "Chiuso" else "Closed",
                if (itL) "Siamo chiusi, riapriamo domani alle 9. Scrivici qui!" else "We're closed, back tomorrow at 9. Text us here!"),
            Template(if (itL) "Riunione" else "Meeting", if (itL) "Sono in riunione, ti scrivo appena esco." else "In a meeting, I'll text you after.")
        ))
        // Statistiche di esempio sugli ultimi giorni
        val cal = java.util.Calendar.getInstance()
        val calls = listOf(6, 9, 4, 11, 8, 13, 10); val sent = listOf(5, 8, 4, 10, 7, 12, 10)
        for (d in 6 downTo 0) {
            val c = (cal.clone() as java.util.Calendar).apply { add(java.util.Calendar.DAY_OF_YEAR, -d) }
            repeat(calls[6 - d]) { p.countCall(c) }
            repeat(sent[6 - d]) { p.countSent(c) }
        }
        repeat(41) { p.countTemplate("Standard") }; repeat(14) { p.countTemplate(if (itL) "Chiuso" else "Closed") }
        listOf("+393471234567", "+393389876543", "+390116543210").forEach { n -> p.addCall(n); p.markSent(n) }
        p.addCall("+393402223344")
        p.sentCount = 12
        p.offEnabled = false; p.workStart = 9 * 60; p.workEnd = 19 * 60; p.weekendOff = true; p.offIndex = 1
        // Allegati di esempio: 3 foto e il catalogo PDF
        val dir = File(app.filesDir, "attach")
        val arr = org.json.JSONArray()
        fun add(k: String, f: String, write: (File) -> Unit) {
            val file = File(dir, f); file.parentFile!!.mkdirs(); write(file)
            arr.put(org.json.JSONObject().put("k", k).put("f", f).put("n", file.name))
        }
        val names = if (itL) listOf("Piscina", "Bungalow", "Spiaggia") else listOf("Pool", "Bungalow", "Beach")
        listOf("piscina", "bungalow", "spiaggia").forEachIndexed { i, n ->
            add("photo", "photo_$i/${names[i]}.jpg") { it.writeBytes(File("/home/claude/shots/foto/$n.jpg").readBytes()) }
        }
        add("pdf", "pdf_1/" + (if (itL) "Listino 2026.pdf" else "Price list 2026.pdf")) { it.writeText("%PDF-1.4") }
        app.getSharedPreferences("call2chat", Context.MODE_PRIVATE).edit().putString("attachments", arr.toString()).commit()
        return app
    }

    private fun main(app: Application, tab: Int, name: String) {
        Prefs(app).lastTab = tab
        val act: Activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        shadowOf(Looper.getMainLooper()).idle()
        save(act.window.decorView, name, 1080, 2340)
        save(act.window.decorView, name + "_full", 1080, 7000)
    }

    private fun run(lang: String) {
        assumeTrue(out != null)
        val app = setup(lang)
        main(app, 0, "${lang}_messaggi")
        main(app, 2, "${lang}_statistiche")
        Prefs(app).offEnabled = true
        main(app, 1, "${lang}_orari")
        Prefs(app).offEnabled = false
        // Scheda durante la chiamata
        Core.onUnknownCall(app, "+393471234567")
        shadowOf(Looper.getMainLooper()).idle()
        val wm = app.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val card = (shadowOf(wm) as ShadowWindowManagerImpl).views.first { (it.layoutParams as? WindowManager.LayoutParams)?.type == WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY }
        save(card, "${lang}_scheda", 1080, 0)
        val setupAct = Robolectric.buildActivity(SetupActivity::class.java).setup().get()
        shadowOf(Looper.getMainLooper()).idle()
        save(setupAct.window.decorView, "${lang}_setup", 1080, 2340)
    }

    @Test @Config(qualifiers = "+it") fun shotsIt() = run("it")
    @Test @Config(qualifiers = "+en") fun shotsEn() = run("en")
}
