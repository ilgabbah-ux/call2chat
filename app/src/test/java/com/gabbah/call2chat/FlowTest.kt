package com.gabbah.call2chat

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FlowTest {
    private val app = ApplicationProvider.getApplicationContext<Application>()

    @Test fun unknownCallShowsNotificationThenOpensWhatsApp() {
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        Core.onUnknownCall(app, "+39 333 1234567")
        val nm = app.getSystemService(NotificationManager::class.java)
        assertEquals(1, shadowOf(nm).allNotifications.size)

        // stesso numero subito dopo: niente seconda notifica, ma finisce nello storico
        Core.onUnknownCall(app, "+393331234567")
        assertEquals(1, shadowOf(nm).allNotifications.size)
        assertEquals(2, Prefs(app).history().size)

        val i = Intent(app, SendActivity::class.java).putExtra(SendActivity.EXTRA_NUMBER, "393331234567")
        ActivityScenario.launch<SendActivity>(i).close()
        val started = shadowOf(app).nextStartedActivity
        assertTrue(started.data.toString().startsWith("https://api.whatsapp.com/send?phone=393331234567&text="))
        assertEquals(1, Prefs(app).sentCount)
        assertTrue(Prefs(app).history().first().sent)
    }

    @Test fun limitReachedOpensPaywall() {
        Prefs(app).sentCount = Prefs.FREE_LIMIT
        val i = Intent(app, SendActivity::class.java).putExtra(SendActivity.EXTRA_NUMBER, "393331234567")
        ActivityScenario.launch<SendActivity>(i).close()
        val started = shadowOf(app).nextStartedActivity
        assertEquals(MainActivity::class.java.name, started.component?.className)
        assertEquals(Prefs.FREE_LIMIT, Prefs(app).sentCount)
    }
}
