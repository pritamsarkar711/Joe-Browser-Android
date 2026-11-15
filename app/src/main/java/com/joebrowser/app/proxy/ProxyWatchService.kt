package com.joebrowser.app.proxy

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.joebrowser.app.JoeApplication
import com.joebrowser.app.MainActivity
import com.joebrowser.app.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * The proxy guard.
 *
 * The guard runs as a foreground service so it keeps watching while the app is in the
 * background: a proxy that dies an hour before a sign-in is exactly the case the user
 * needs to be told about, and a service that stops when the app is closed cannot tell
 * them.
 */
class ProxyWatchService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var monitor: ProxyMonitor? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundCompat()
        if (monitor == null) {
            val m = ProxyMonitor(this, scope) { event -> notifyEvent(event) }
            monitor = m
            m.start()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        monitor?.stop()
        monitor = null
        scope.cancel()
        super.onDestroy()
    }

    private fun startForegroundCompat() {
        val notification = NotificationCompat.Builder(this, JoeApplication.CHANNEL_PROXY)
            .setContentTitle(getString(R.string.proxy_watch_title))
            .setContentText(getString(R.string.proxy_watch_running))
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(
                PendingIntent.getActivity(
                    this, 0, Intent(this, MainActivity::class.java),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
            )
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    /**
     * Turns a watch event into a notification.
     */
    private fun notifyEvent(event: ProxyMonitor.ProxyEvent) {
        val manager = getSystemService(android.app.NotificationManager::class.java) ?: return
        val (title, text) = when (event.kind) {
            ProxyMonitor.ProxyEvent.Kind.OFFLINE ->
                "The proxy for \"${event.profileName}\" is not answering" to
                    "Check it before you sign in. ${event.detail}"

            ProxyMonitor.ProxyEvent.Kind.BACK_ONLINE ->
                "The proxy for \"${event.profileName}\" is answering again" to
                    "The exit address is ${event.to}"

            ProxyMonitor.ProxyEvent.Kind.ROTATED ->
                "The proxy for \"${event.profileName}\" changed address" to
                    "${event.from} → ${event.to}" + if (event.detail.isNotBlank()) " (${event.detail})" else "" +
                        ". Pause account activity and check the session."

            ProxyMonitor.ProxyEvent.Kind.FELL_BACK ->
                "The proxy for \"${event.profileName}\" stopped responding" to
                    "The session is on a direct connection, so the real address is visible."
        }

        val notification = NotificationCompat.Builder(this, JoeApplication.CHANNEL_PROXY)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(
                PendingIntent.getActivity(
                    this, event.profileName.hashCode(), Intent(this, MainActivity::class.java),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
            )
            .build()
        try {
            manager.notify(event.profileName.hashCode(), notification)
        } catch (e: SecurityException) {
            // The notification permission was refused. Nothing to do about it.
        }
    }

    companion object {
        private const val NOTIFICATION_ID = 4711

        /** Starts the guard, if the user has allowed notifications. */
        fun start(context: Context) {
            try {
                val intent = Intent(context, ProxyWatchService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                // The guard is a convenience, not a requirement: the app still works without it.
            }
        }

        fun stop(context: Context) {
            try {
                context.stopService(Intent(context, ProxyWatchService::class.java))
            } catch (e: Exception) {}
        }
    }
}
