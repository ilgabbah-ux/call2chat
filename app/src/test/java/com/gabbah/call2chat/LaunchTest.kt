package com.gabbah.call2chat

import androidx.test.core.app.ActivityScenario
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LaunchTest {
    @Test fun mainOpens() {
        ActivityScenario.launch(MainActivity::class.java).use { s ->
            s.onActivity { a -> a.recreate() }
        }
    }
}
