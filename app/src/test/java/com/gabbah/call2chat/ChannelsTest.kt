package com.gabbah.call2chat

import android.app.Application
import android.content.Intent
import android.content.pm.PackageInfo
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "it")
class ChannelsTest {
    private val app = ApplicationProvider.getApplicationContext<Application>()
    private fun install(pkg: String) = shadowOf(app.packageManager).installPackage(PackageInfo().apply { packageName = pkg })

    @Test fun autoPicksWhatsAppThenBusinessThenSms() {
        assertEquals(Channel.SMS, Channel.resolve(app, Channel.AUTO))
        install("com.whatsapp.w4b")
        assertEquals(Channel.WA_BUSINESS, Channel.resolve(app, Channel.AUTO))
        install("com.whatsapp")
        assertEquals(Channel.WHATSAPP, Channel.resolve(app, Channel.AUTO))
        assertTrue(Health.bothWhatsApp(app))
    }

    @Test fun smsIsPrefilled() {
        Channel.open(app, Channel.SMS, "393331234567", "Ciao")
        val i = shadowOf(app).nextStartedActivity
        assertEquals(Intent.ACTION_SENDTO, i.action)
        assertEquals("smsto:+393331234567", i.data.toString())
        assertEquals("Ciao", i.getStringExtra("sms_body"))
    }

    @Test fun whatsappWithAttachmentsOpensTheChatDirectly() {
        install("com.whatsapp")
        val dir = File(app.filesDir, "attach").apply { mkdirs() }
        File(dir, "photo_1.jpg").writeText("x"); File(dir, "pdf_1.pdf").writeText("y")
        app.getSharedPreferences("call2chat", 0).edit().putString("attachments",
            """[{"k":"photo","f":"photo_1.jpg","n":"a.jpg"},{"k":"pdf","f":"pdf_1.pdf","n":"Catalogo.pdf"}]""").apply()
        assertEquals("1 foto · PDF", Attachments.summary(app))
        val steps = Channel.plan(app, Channel.WHATSAPP, "393331234567", "Ciao")
        assertEquals(2, steps.size)
        // 1) foto con didascalia, direttamente nella chat del numero
        val a = steps[0]
        assertEquals(Intent.ACTION_SEND, a.action)
        assertEquals("image/*", a.type)
        assertEquals("com.whatsapp", a.`package`)
        assertEquals("393331234567@s.whatsapp.net", a.getStringExtra("jid"))
        assertEquals("Ciao", a.getStringExtra(Intent.EXTRA_TEXT))
        // 2) il PDF da solo, stessa chat
        val b = steps[1]
        assertEquals("application/pdf", b.type)
        assertEquals("393331234567@s.whatsapp.net", b.getStringExtra("jid"))
        assertEquals(null, b.getStringExtra(Intent.EXTRA_TEXT))
        // l'anteprima include gli allegati
        assertEquals(Intent.EXTRA_STREAM in Channel.plan(app, Channel.WHATSAPP, null, "Ciao")[0].extras!!.keySet(), true)
    }

    @Test fun perSimChannelAndSwitch() {
        val p = Prefs(app)
        p.channel = Channel.WHATSAPP
        p.setSimChannel(7, Channel.WA_BUSINESS)
        assertEquals(Channel.WA_BUSINESS, p.channelFor(7))
        assertEquals(Channel.WHATSAPP, p.channelFor(3))
        assertEquals(Channel.WHATSAPP, p.channelFor(-1))
        p.setSimEnabled(7, false)
        Core.onUnknownCall(app, "+393331234567", 7)
        assertEquals(0, p.history().size)
        Core.onUnknownCall(app, "+393331234567", 3)
        assertEquals(3, p.history().first().sim)
    }

    @Test fun wechatCopiesTheText() {
        install("com.tencent.mm")
        Channel.open(app, Channel.WECHAT, "8613800000000", "你好")
        val clip = app.getSystemService(android.content.ClipboardManager::class.java).primaryClip
        assertEquals("你好", clip!!.getItemAt(0).text.toString())
    }
}
