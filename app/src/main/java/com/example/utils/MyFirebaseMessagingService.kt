package com.example.utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.example.data.AppDao
import com.example.data.NoticeEntity

class MyFirebaseMessagingService : FirebaseMessagingService() {
    private val TAG = "MyFirebaseMessagingService"

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "New FCM Device Token: $token")
        // Store latest device token in SharedPreferences for easy administrative viewing
        try {
            val prefs = getSharedPreferences("dark_store_fcm_prefs", Context.MODE_PRIVATE)
            prefs.edit().putString("fcm_token", token).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        Log.d(TAG, "Received message from FCM. Sender: ${remoteMessage.from}")

        // Chat messages ("someone messaged you") are data-only pushes sent by the
        // chat-notifier worker — handled separately from notices/announcements.
        if (remoteMessage.data["type"] == "chat") {
            handleChatPush(remoteMessage.data)
            return
        }

        // Rich pushes from the Dark Store notifier: new app, app update,
        // submission (to admins), submission status (to the developer), announcement.
        when (remoteMessage.data["type"]) {
            "new_app", "app_update", "submission_new", "submission_status", "announcement" -> {
                handleRichPush(remoteMessage.data)
                return
            }
        }

        val notificationTitle = remoteMessage.notification?.title ?: remoteMessage.data["title"] ?: "Platform Alert"
        val notificationBody = remoteMessage.notification?.body ?: remoteMessage.data["message"] ?: remoteMessage.data["body"] ?: "New notice received"
        val imageUrl = remoteMessage.data["imageUrl"] ?: ""
        val targetAppId = remoteMessage.data["targetAppId"] ?: "all"
        val noticeId = remoteMessage.data["id"] ?: "ntc_${System.currentTimeMillis()}"
        val timestamp = remoteMessage.data["timestamp"]?.toLongOrNull() ?: System.currentTimeMillis()

        Log.d(TAG, "Received Notice via FCM. Title: $notificationTitle, Message: $notificationBody")

        // CLIENT-SIDE FILTERING SYSTEM BASED ON USER SETTINGS
        val sharedPrefs = getSharedPreferences("dark_store_pref", Context.MODE_PRIVATE)
        val notifyNewApps = sharedPrefs.getBoolean("notify_new_apps", true)
        val notifyUpdates = sharedPrefs.getBoolean("notify_updates", true)
        val notifyAnnouncements = sharedPrefs.getBoolean("notify_announcements", true)
        val notifySubmissions = sharedPrefs.getBoolean("notify_submissions", true)

        var isAllowed = true
        val titleLower = notificationTitle.lowercase()

        if (titleLower.contains("submission") || titleLower.contains("approved") || titleLower.contains("rejected")) {
            if (!notifySubmissions) {
                isAllowed = false
                Log.d(TAG, "Notification blocked: Submission alerts disabled.")
            }
        } else if (titleLower.contains("new app")) {
            if (!notifyNewApps) {
                isAllowed = false
                Log.d(TAG, "Notification blocked: New App alerts disabled.")
            }
        } else if (titleLower.contains("update pack") || titleLower.contains("update available") || titleLower.contains("changelog")) {
            if (!notifyUpdates) {
                isAllowed = false
                Log.d(TAG, "Notification blocked: App Update alerts disabled.")
            }
        } else {
            // General / announcements
            if (!notifyAnnouncements) {
                isAllowed = false
                Log.d(TAG, "Notification blocked: Announcement alerts disabled.")
            }
        }

        if (!isAllowed) {
            return
        }

        // Save notice instantly to localized DAO Cache so it appears on Notice Dashboard as UNREAD
        try {
            val appDao = AppDao(applicationContext)
            appDao.loadCachedNotices()
            val list = appDao.getNoticesList().toMutableList()
            if (list.none { it.id == noticeId }) {
                list.add(0, NoticeEntity(
                    id = noticeId,
                    title = notificationTitle,
                    message = notificationBody,
                    imageUrl = imageUrl,
                    timestamp = timestamp,
                    targetAppId = targetAppId,
                    isRead = false // Saved as unread entry!
                ))
                appDao.insertNotices(list)
                Log.d(TAG, "Notice successfully stored to DAO caching layer")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed store notice payload: ${e.message}", e)
        }

        // Show systemic push notification banner
        sendNotification(noticeId, notificationTitle, notificationBody, targetAppId)
    }

    // ------------------------------------------------------------------
    // RICH PUSHES (new app / update / submission / status / announcement)
    // ------------------------------------------------------------------
    private fun handleRichPush(data: Map<String, String>) {
        try {
            val type = data["type"].orEmpty()
            val prefs = getSharedPreferences("dark_store_pref", Context.MODE_PRIVATE)
            val allowed = when (type) {
                "new_app" -> prefs.getBoolean("notify_new_apps", true)
                "app_update" -> prefs.getBoolean("notify_updates", true)
                "submission_new", "submission_status" -> prefs.getBoolean("notify_submissions", true)
                else -> prefs.getBoolean("notify_announcements", true)
            }
            if (!allowed) return

            val id = data["id"].orEmpty().ifBlank { "ntc_${System.currentTimeMillis()}" }
            val title = data["title"].orEmpty().ifBlank { "Dark Store" }
            val message = data["message"].orEmpty()
            val imageUrl = data["imageUrl"].orEmpty()
            val bannerUrl = data["bannerUrl"].orEmpty()
            val target = data["targetAppId"].orEmpty().ifBlank { "all" }
            val timestamp = data["timestamp"]?.toLongOrNull() ?: System.currentTimeMillis()

            // Keep a copy in the in-app notice dashboard (unread).
            try {
                val dao = AppDao(applicationContext)
                dao.loadCachedNotices()
                val list = dao.getNoticesList().toMutableList()
                if (list.none { it.id == id }) {
                    list.add(0, NoticeEntity(id = id, title = title, message = message, imageUrl = imageUrl,
                        timestamp = timestamp, targetAppId = target, isRead = false))
                    dao.insertNotices(list)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to store rich notice: ${e.message}")
            }

            val (channelId, channelName, accent) = when (type) {
                "new_app" -> Triple("new_apps_channel", "New apps", 0xFF34D399.toInt())
                "app_update" -> Triple("app_updates_channel", "App updates", 0xFFF59E0B.toInt())
                "submission_new" -> Triple("submissions_admin_channel", "New submissions (admin)", 0xFF3B82F6.toInt())
                "submission_status" ->
                    if (data["status"] == "Rejected") Triple("submission_status_channel", "Submission status", 0xFFEF4444.toInt())
                    else Triple("submission_status_channel", "Submission status", 0xFF34D399.toInt())
                else -> Triple("announcements_channel", "Dark Store Announcements", 0xFF8B5CF6.toInt())
            }
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                nm.createNotificationChannel(
                    NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_HIGH)
                )
            }

            val intent = Intent().apply {
                setClassName(packageName, "com.example.MainActivity")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("view_notice_id", id)
                when {
                    target.startsWith("submission:") || type.startsWith("submission") ->
                        putExtra("open_screen", "submissions")
                    target.startsWith("update:") -> {
                        putExtra("open_screen", "updates")
                        putExtra("app_id", target.substringAfter("update:"))
                    }
                    target != "all" -> {
                        putExtra("open_screen", "app_details")
                        putExtra("app_id", target)
                    }
                    else -> putExtra("open_screen", "announcements")
                }
            }
            val pi = PendingIntent.getActivity(
                this, id.hashCode(), intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val builder = NotificationCompat.Builder(this, channelId)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setColor(accent)
                .setContentTitle(title)
                .setContentText(message)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
                .setContentIntent(pi)
                .setAutoCancel(true)
                .setWhen(timestamp)
                .setGroup(channelId)

            // App icon / logo shown big on the right of the notification.
            downloadBitmap(imageUrl, 256)?.let { builder.setLargeIcon(roundedBitmap(it)) }

            val banner = if (type == "new_app") downloadBitmap(bannerUrl, 1024) else null
            if (banner != null) {
                builder.setStyle(
                    NotificationCompat.BigPictureStyle()
                        .bigPicture(banner)
                        .setSummaryText(message)
                        .bigLargeIcon(null as Bitmap?)
                )
            } else {
                builder.setStyle(NotificationCompat.BigTextStyle().bigText(message))
            }
            nm.notify(id.hashCode(), builder.build())
        } catch (e: Exception) {
            Log.e(TAG, "Error showing rich push: ${e.message}", e)
        }
    }

    /** Blocking download — fine here: onMessageReceived already runs on a background thread (~20s budget). */
    private fun downloadBitmap(url: String, maxSide: Int): Bitmap? {
        if (!url.startsWith("http")) return null
        return try {
            val conn = java.net.URL(url).openConnection() as java.net.HttpURLConnection
            conn.connectTimeout = 5000
            conn.readTimeout = 7000
            conn.inputStream.use { input ->
                val raw = BitmapFactory.decodeStream(input) ?: return null
                val ratio = maxSide.toFloat() / maxOf(raw.width, raw.height)
                if (ratio < 1f) Bitmap.createScaledBitmap(raw, (raw.width * ratio).toInt(), (raw.height * ratio).toInt(), true) else raw
            }
        } catch (e: Throwable) {
            null
        }
    }

    private fun roundedBitmap(src: Bitmap): Bitmap {
        val size = minOf(src.width, src.height)
        val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        canvas.drawRoundRect(RectF(0f, 0f, size.toFloat(), size.toFloat()), size * 0.22f, size * 0.22f, paint)
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        canvas.drawBitmap(src, (size - src.width) / 2f, (size - src.height) / 2f, paint)
        return out
    }

    private fun handleChatPush(data: Map<String, String>) {
        try {
            val prefs = getSharedPreferences("dark_store_pref", Context.MODE_PRIVATE)
            if (!prefs.getBoolean("notify_messages", true)) return

            val chatId = data["chatId"].orEmpty()
            val senderUid = data["senderUid"].orEmpty()
            val senderName = data["senderName"].orEmpty().ifBlank { "New message" }
            val senderPhoto = data["senderPhoto"].orEmpty()
            val body = data["body"].orEmpty().ifBlank { "Sent you a message" }
            val unread = data["unread"]?.toIntOrNull() ?: 1

            // Already looking at this exact conversation → no banner needed.
            if (ChatPushState.appInForeground && chatId.isNotBlank() && chatId == ChatPushState.activeChatId) return

            val channelId = "chat_messages_channel"
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    channelId, "Chat messages", NotificationManager.IMPORTANCE_HIGH
                ).apply { description = "Messages from other Dark Store users and developers" }
                nm.createNotificationChannel(channel)
            }

            val intent = Intent().apply {
                setClassName(packageName, "com.example.MainActivity")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("open_screen", "chat")
                putExtra("chat_uid", senderUid)
                putExtra("chat_name", senderName)
                putExtra("chat_photo", senderPhoto)
            }
            val pi = PendingIntent.getActivity(
                this, chatId.hashCode(), intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val builder = NotificationCompat.Builder(this, channelId)
                .setSmallIcon(android.R.drawable.ic_dialog_email)
                .setContentTitle(senderName)
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setNumber(unread)
                .setContentIntent(pi)
                .setAutoCancel(true)
            // One notification per conversation — newer messages replace the old one.
            nm.notify(("chat_" + chatId).hashCode(), builder.build())
        } catch (e: Exception) {
            Log.e(TAG, "Error showing chat push: ${e.message}", e)
        }
    }

    private fun sendNotification(noticeId: String, title: String, messageBody: String, targetAppId: String) {
        try {
            val channelId = "announcements_channel"
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    channelId,
                    "Dark Store Announcements",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Global notifications sent by administrators"
                }
                notificationManager.createNotificationChannel(channel)
            }

            // ADAPTIVE DEEP LINKS RESOLUTION FOR NOTIFICATION CLICK ACTIONS
            val intent = Intent().apply {
                setClassName(packageName, "com.example.MainActivity")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("view_notice_id", noticeId)
                
                if (targetAppId.startsWith("approved_") || targetAppId.startsWith("rejected_")) {
                    putExtra("open_screen", "submissions")
                } else if (targetAppId.startsWith("update:")) {
                    putExtra("open_screen", "updates")
                    putExtra("app_id", targetAppId.substringAfter("update:"))
                } else if (targetAppId != "all" && !targetAppId.startsWith("token:")) {
                    putExtra("open_screen", "app_details")
                    putExtra("app_id", targetAppId)
                } else {
                    putExtra("open_screen", "announcements")
                }
            }

            val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }

            val pendingIntent = PendingIntent.getActivity(
                this,
                noticeId.hashCode(),
                intent,
                pendingIntentFlags
            )

            val builder = NotificationCompat.Builder(this, channelId)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title)
                .setContentText(messageBody)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setStyle(NotificationCompat.BigTextStyle().bigText(messageBody))

            notificationManager.notify(noticeId.hashCode(), builder.build())
        } catch (e: Exception) {
            Log.e(TAG, "Error displaying push alert banner: ${e.message}", e)
        }
    }
}
