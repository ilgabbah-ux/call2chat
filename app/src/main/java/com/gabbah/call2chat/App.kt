package com.gabbah.call2chat

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        Themes.apply(this)
        val ch = NotificationChannel(
            CHANNEL_CALLS,
            getString(R.string.channel_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply { description = getString(R.string.channel_desc) }
        getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
    }

    companion object {
        const val CHANNEL_CALLS = "unknown_calls"
    }
}
