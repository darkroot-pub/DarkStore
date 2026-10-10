package com.example.utils

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

/**
 * Foreground service that exists ONLY while an app download is actively in
 * progress. It is started right before a download begins and stopped as soon
 * as no downloads remain active — unlike the old always-on background sync
 * service, it does not run continuously and does not poll anything itself.
 *
 * Its only job is to hold a foreground-priority process so Android (and
 * aggressive OEM battery managers on devices like Redmi/Xiaomi or Samsung)
 * don't kill the download mid-transfer if the app is backgrounded. The actual
 * per-file progress notification is still handled separately by
 * [NotificationHelper]; this service just shows one small ongoing summary
 * notification, which Android requires for any foreground service to run.
 */
class DownloadForegroundService : Service() {

    companion object {
        private const val CHANNEL_ID = "download_service_channel"
        private const val NOTIFICATION_ID = 2001
        private const val ACTION_STOP = "com.example.action.STOP_DOWNLOAD_SERVICE"

        /** Call right before starting a download. Safe to call repeatedly. */
        fun ensureStarted(context: Context) {
            try {
                val intent = Intent(context, DownloadForegroundService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                android.util.Log.e("DownloadFgService", "Failed to start: ${e.message}")
            }
        }

        /** Call after a download finishes/fails/cancels; stops itself only if nothing else is active. */
        fun stopIfNoActiveDownloads(context: Context) {
            try {
                val intent = Intent(context, DownloadForegroundService::class.java)
                    .setAction(ACTION_STOP)
                context.startService(intent)
            } catch (e: Exception) {
                android.util.Log.e("DownloadFgService", "Failed to stop: ${e.message}")
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            val stillActive = try {
                com.example.data.AppDao(applicationContext).getDownloads().value
                    .any { it.status == "DOWNLOADING" }
            } catch (e: Exception) {
                false
            }
            if (!stillActive) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                } else {
                    @Suppress("DEPRECATION")
                    stopForeground(true)
                }
                stopSelf()
            }
            return START_NOT_STICKY
        }

        createChannel()
        val notification = buildNotification()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            android.util.Log.e("DownloadFgService", "startForeground failed: ${e.message}")
        }
        // Not sticky: if the OS kills the process, we don't want an empty
        // foreground notification resurrecting itself with nothing to show for it.
        return START_NOT_STICKY
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                CHANNEL_ID,
                "App Downloads",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps an app download running while Dark Store is in the background"
                setShowBadge(false)
            }
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val notificationIntent = Intent().apply {
            setClassName(packageName, "com.example.MainActivity")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val pendingIntent = PendingIntent.getActivity(this, 77, notificationIntent, pendingIntentFlags)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Downloading")
            .setContentText("Dark Store is downloading an app in the background")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .build()
    }
}
