package com.gabbah.call2chat

import android.app.Application
import android.content.Context
import android.os.Looper
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSettings
import org.robolectric.shadows.ShadowWindowManagerImpl
import java.util.Calendar
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "it")
class FeaturesTest {
    private val app = ApplicationProvider.getApplicationContext<Application>()

    private fun cal(y: Int, mo: Int, d: Int, h: Int, mi: Int) =
        Calendar.getInstance().apply { clear(); set(y, mo - 1, d, h, mi) }

    @Test fun variables() {
        val c = cal(2026, 11, 27, 14, 5) // venerdì
        val it = Locale.ITALIAN
        assertEquals("ore 14:05, 15:05", Vars.render("ore {ora}, {ora+1}", c, it))
        assertEquals("venerdì sabato 27/11", Vars.render("{giorno} {domani} {data}", c, it))
        assertEquals("16:05", Vars.render("{time+2}", c, it))
        assertEquals("{ciao} resta", Vars.render("{ciao} resta", c, it))
    }

    @Test fun offHours() {
        val s = Schedule(true, 9 * 60, 19 * 60, true, 3)
        assertFalse(s.isOffHours(cal(2026, 11, 27, 10, 0)))   // venerdì mattina
        assertTrue(s.isOffHours(cal(2026, 11, 27, 20, 0)))    // venerdì sera
        assertTrue(s.isOffHours(cal(2026, 11, 28, 10, 0)))    // sabato
        assertFalse(s.copy(weekendOff = false).isOffHours(cal(2026, 11, 28, 10, 0)))
        assertFalse(s.copy(enabled = false).isOffHours(cal(2026, 11, 27, 22, 0)))
    }

    @Test fun templatesAndAutoChoice() {
        val p = Prefs(app)
        val list = p.templates()
        assertEquals(4, list.size)
        assertEquals("Fuori orario", list[3].name)
        p.offEnabled = true
        assertEquals(3, p.autoIndex(cal(2026, 11, 27, 22, 0)))
        assertEquals(0, p.autoIndex(cal(2026, 11, 27, 11, 0)))
        assertTrue(p.messageFor(1, cal(2026, 11, 27, 14, 0)).contains("15:00"))
    }

    @Test fun oldMessageKeptOnUpgrade() {
        app.getSharedPreferences("call2chat", Context.MODE_PRIVATE).edit().putString("message", "Nurral Camping Village").apply()
        assertEquals("Nurral Camping Village", Prefs(app).templates()[0].text)
    }

    @Test fun stats() {
        val p = Prefs(app)
        val now = cal(2026, 11, 27, 10, 0)
        p.countCall(now); p.countCall(now); p.countSent(now)
        val yesterday = cal(2026, 11, 26, 10, 0)
        p.countCall(yesterday)
        val s = p.stats(now)
        assertEquals(3, s.calls7)
        assertEquals(1, s.sent7)
        assertEquals(7, s.days.size)
        assertEquals(2, s.days.last().second)
    }

    @Test fun cardLetsYouPickAnotherMessage() {
        ShadowSettings.setCanDrawOverlays(true)
        Core.onUnknownCall(app, "+393421112233")
        shadowOf(Looper.getMainLooper()).idle()
        val wm = app.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val root = (shadowOf(wm) as ShadowWindowManagerImpl).views.single()
        fun all(v: android.view.View): List<android.view.View> =
            listOf(v) + if (v is ViewGroup) (0 until v.childCount).flatMap { all(v.getChildAt(it)) } else emptyList()
        val views = all(root)
        views.filterIsInstance<TextView>().first { it.text == "Sono in moto" }.performClick()
        assertTrue(views.filterIsInstance<TextView>().any { it.text.startsWith("Ciao! Sono in moto") })
        views.filterIsInstance<Button>().first { it.text.contains("WhatsApp") }.performClick()
        shadowOf(Looper.getMainLooper()).idle()
        val started = shadowOf(app).nextStartedActivity
        assertEquals(2, started.getIntExtra(SendActivity.EXTRA_TEMPLATE, -1))
        assertEquals(1, Prefs(app).stats().calls7)
    }

    @Test fun setupAndMainOpen() {
        ActivityScenario.launch(SetupActivity::class.java).use { s ->
            s.onActivity { a ->
                assertEquals("Passo 1 di ${Health.steps().size}", a.findViewById<TextView>(R.id.progress).text)
                a.findViewById<Button>(R.id.skip).performClick()
                a.recreate()
            }
        }
        ActivityScenario.launch(MainActivity::class.java).use { s ->
            s.onActivity { a ->
                assertTrue(a.findViewById<TextView>(R.id.healthTitle).text.isNotEmpty())
                a.findViewById<Button>(R.id.btnTplAdd).performClick()
                assertEquals(5, Prefs(a).templates().size)
                a.recreate()
            }
        }
    }
}
