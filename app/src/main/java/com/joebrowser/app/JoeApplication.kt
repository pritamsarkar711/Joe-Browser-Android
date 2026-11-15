package com.joebrowser.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.joebrowser.app.data.store.ProfileStore

/**
 * The application.
 */
class JoeApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        createChannels()
        // The library is read once at startup so the first screen is never empty for a
        // reason that has nothing to do with the screen.
        ProfileStore(this).load()
    }

    private fun createChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_PROXY,
            getString(R.string.notification_channel_proxy),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = getString(R.string.notification_channel_proxy_desc)
            enableVibration(true)
        }
        manager.createNotificationChannel(channel)
    }

    companion object {
        const val CHANNEL_PROXY = "joe.proxy.alerts"
    }
}
