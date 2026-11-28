package com.gabbah.call2chat

import android.app.Application
import android.content.Intent
import android.content.pm.PackageInfo
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "it")
class SendFlowTest {
    @Test fun pdfGoesAfterReturningFromPhotos() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        shadowOf(app.packageManager).installPackage(PackageInfo().apply { packageName = "com.whatsapp.w4b" })
        Prefs(app).channel = Channel.WA_BUSINESS
        val dir = File(app.filesDir, "attach").apply { mkdirs() }
        File(dir, "p.jpg").writeText("x"); File(dir, "v.mp4").writeText("v"); File(dir, "d.pdf").writeText("y")
        app.getSharedPreferences("call2chat", 0).edit().putString("attachments",
            """[{"k":"photo","f":"p.jpg","n":"p.jpg"},{"k":"video","f":"v.mp4","n":"v.mp4"},{"k":"pdf","f":"d.pdf","n":"d.pdf"}]""").apply()

        val i = Intent(app, SendActivity::class.java).putExtra(SendActivity.EXTRA_NUMBER, "393331234567")
        val c = Robolectric.buildActivity(SendActivity::class.java, i).create().start().resume()
        val first = shadowOf(c.get()).nextStartedActivity
        assertEquals("image/*", first.type)
        assertEquals("com.whatsapp.w4b", first.`package`)
        assertEquals("393331234567@s.whatsapp.net", first.getStringExtra("jid"))
        assertNull(shadowOf(c.get()).nextStartedActivity)          // il video aspetta
        c.pause().stop().restart().start().resume()                 // torno da WhatsApp
        assertEquals("video/*", shadowOf(c.get()).nextStartedActivity.type)
        assertNull(shadowOf(c.get()).nextStartedActivity)
        c.pause().stop().restart().start().resume()
        val third = shadowOf(c.get()).nextStartedActivity
        assertEquals("application/pdf", third.type)
        c.pause().stop().restart().start().resume()
        assertTrue(c.get().isFinishing)
        assertEquals(1, Prefs(app).sentCount)
    }
}
