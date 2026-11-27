package com.gabbah.call2chat

import android.app.Application
import android.content.Context
import android.os.Looper
import android.view.WindowManager
import android.widget.Button
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSettings
import org.robolectric.shadows.ShadowWindowManagerImpl

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OverlayTest {
    @Test fun sendButtonAppearsDuringCallAndOpensWhatsApp() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        ShadowSettings.setCanDrawOverlays(true)
        Core.onUnknownCall(app, "+393421112233")
        shadowOf(Looper.getMainLooper()).idle()
        val wm = app.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val views = (shadowOf(wm) as ShadowWindowManagerImpl).views
        assertEquals(1, views.size)
        fun buttons(v: android.view.View): List<Button> = when (v) {
            is Button -> listOf(v)
            is android.view.ViewGroup -> (0 until v.childCount).flatMap { buttons(v.getChildAt(it)) }
            else -> emptyList()
        }
        val send = buttons(views[0]).first { it.text.toString().contains("WhatsApp") }
        send.performClick()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(0, (shadowOf(wm) as ShadowWindowManagerImpl).views.size)
        assertEquals(SendActivity::class.java.name, shadowOf(app).nextStartedActivity.component?.className)
    }
}
