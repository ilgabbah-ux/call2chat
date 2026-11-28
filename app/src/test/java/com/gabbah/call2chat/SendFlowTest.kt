package com.gabbah.call2chat

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.os.Looper
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSettings
import org.robolectric.shadows.ShadowWindowManagerImpl
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "it")
class SendFlowTest {
    private val app = ApplicationProvider.getApplicationContext<Application>()

    private fun setup() {
        shadowOf(app.packageManager).installPackage(PackageInfo().apply { packageName = "com.whatsapp.w4b" })
        Prefs(app).channel = Channel.WA_BUSINESS
        val dir = File(app.filesDir, "attach").apply { mkdirs() }
        File(dir, "p.jpg").writeText("x"); File(dir, "v.mp4").writeText("v"); File(dir, "d.pdf").writeText("y")
        app.getSharedPreferences("call2chat", 0).edit().putString("attachments",
            """[{"k":"photo","f":"p.jpg","n":"p.jpg"},{"k":"video","f":"v.mp4","n":"v.mp4"},{"k":"pdf","f":"d.pdf","n":"d.pdf"}]""").apply()
    }

    private fun barButton(): Button {
        val wm = app.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val root = (shadowOf(wm) as ShadowWindowManagerImpl).views.last()
        fun all(v: android.view.View): List<android.view.View> =
            listOf(v) + if (v is ViewGroup) (0 until v.childCount).flatMap { all(v.getChildAt(it)) } else emptyList()
        return all(root).filterIsInstance<Button>().single()
    }

    @Test fun photosThenBarForVideoThenPdf() {
        setup()
        ShadowSettings.setCanDrawOverlays(true)
        val i = Intent(app, SendActivity::class.java).putExtra(SendActivity.EXTRA_NUMBER, "393331234567")
        Robolectric.buildActivity(SendActivity::class.java, i).create()
        val first = shadowOf(app).nextStartedActivity
        assertEquals("image/*", first.type)
        assertEquals("393331234567@s.whatsapp.net", first.getStringExtra("jid"))
        shadowOf(Looper.getMainLooper()).idle()

        // la barra sopra WhatsApp propone il video
        val b1 = barButton()
        assertEquals("Invia video", b1.text.toString())
        b1.performClick()
        val trampoline = shadowOf(app).nextStartedActivity
        assertTrue(trampoline.getBooleanExtra(SendActivity.EXTRA_NEXT, false))
        Robolectric.buildActivity(SendActivity::class.java, trampoline).create()
        val video = shadowOf(app).nextStartedActivity
        assertEquals("video/*", video.type)
        assertEquals("393331234567@s.whatsapp.net", video.getStringExtra("jid"))
        shadowOf(Looper.getMainLooper()).idle()

        // poi il PDF
        val b2 = barButton()
        assertEquals("Invia PDF", b2.text.toString())
        b2.performClick()
        Robolectric.buildActivity(SendActivity::class.java, shadowOf(app).nextStartedActivity).create()
        assertEquals("application/pdf", shadowOf(app).nextStartedActivity.type)
        assertTrue(!NextBar.hasNext())
        assertEquals(1, Prefs(app).sentCount)
    }
}
