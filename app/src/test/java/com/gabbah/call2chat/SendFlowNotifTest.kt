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
class SendFlowNotifTest {
    private val app = ApplicationProvider.getApplicationContext<Application>()

    private fun setup() {
        shadowOf(app.packageManager).installPackage(PackageInfo().apply { packageName = "com.whatsapp.w4b" })
        Prefs(app).channel = Channel.WA_BUSINESS
        val dir = File(app.filesDir, "attach").apply { mkdirs() }
        File(dir, "p.jpg").writeText("x"); File(dir, "v.mp4").writeText("v"); File(dir, "d.pdf").writeText("y")
        app.getSharedPreferences("call2chat", 0).edit().putString("attachments",
            """[{"k":"photo","f":"p.jpg","n":"p.jpg"},{"k":"video","f":"v.mp4","n":"v.mp4"},{"k":"pdf","f":"d.pdf","n":"d.pdf"}]""").apply()
    }

    @Test fun withoutOverlayUsesANotification() {
        setup()
        ShadowSettings.setCanDrawOverlays(false)
        shadowOf(app).grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS)
        val i = Intent(app, SendActivity::class.java).putExtra(SendActivity.EXTRA_NUMBER, "393331234567")
        Robolectric.buildActivity(SendActivity::class.java, i).create()
        val nm = app.getSystemService(android.app.NotificationManager::class.java)
        val n = shadowOf(nm).allNotifications.single()
        assertTrue(n.extras.getString("android.title")!!.contains("video"))
    }
}
