package com.example.notificationlogger

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.content.pm.ServiceInfo
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.notificationlogger.data.AppDatabase
import com.example.notificationlogger.data.Message
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class NotificationListener : NotificationListenerService() {

    private lateinit var mediaInterceptor: MediaInterceptor

    companion object {
        private const val TAG = "NotificationListener"
        private const val CHANNEL_ID = "whatsapp_logger_channel"
        private const val NOTIFICATION_ID = 1001
    }

    override fun onCreate() {
        super.onCreate()
        mediaInterceptor = MediaInterceptor(applicationContext)
        mediaInterceptor.startWatching()
        
        createNotificationChannel()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, createForegroundNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, createForegroundNotification())
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn?.let {
            val pkg = it.packageName
            if (pkg == "com.whatsapp" || pkg == "com.whatsapp.w4b") {
                val extras = it.notification.extras
                val title = extras.getString(Notification.EXTRA_TITLE) ?: ""
                val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
                val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString() ?: ""

                // Combine text if it's long
                val content = if (bigText.length > text.length) bigText else text

                // Extract sender name
                val sender = title.trim()
                if (sender.isEmpty() || sender == "WhatsApp") return // Ignore generic "WhatsApp" titles

                // Special handling for WhatsApp "View once" and "Deleted" messages
                val isDeletedNotification = content.contains("This message was deleted", ignoreCase = true)
                val isViewOnce = content.contains("View once photo", ignoreCase = true) || 
                                 content.contains("View once video", ignoreCase = true)

                // Ignore background sync notifications
                if (content.contains("Checking for new messages", ignoreCase = true)) return
                if (content.contains("WhatsApp Web is currently active", ignoreCase = true)) return

                if (isDeletedNotification) {
                    markMessageAsDeleted(sender, pkg)
                } else if (content.isNotEmpty()) {
                    saveMessage(sender, content, pkg, isViewOnce)
                    Log.d(TAG, "Message processed from: $sender in $pkg")
                }
            }
        }
    }

    private fun markMessageAsDeleted(sender: String, packageName: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = AppDatabase.getDatabase(applicationContext)
                val latestMessage = db.messageDao().getLatestMessageFromSender(sender, packageName)
                latestMessage?.let {
                    val updated = it.copy(isDeleted = true)
                    db.messageDao().updateMessage(updated)
                    Log.d(TAG, "Marked last message from $sender as deleted")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error marking message as deleted: ${e.message}")
            }
        }
    }

    private fun saveMessage(sender: String, content: String, packageName: String, isViewOnce: Boolean) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = AppDatabase.getDatabase(applicationContext)
                
                // Prevent duplicate saving of the same notification (within 5 seconds)
                val fiveSecondsAgo = System.currentTimeMillis() - 5000
                val duplicate = db.messageDao().findDuplicate(sender, content, packageName, fiveSecondsAgo)
                
                if (duplicate == null) {
                    val messageText = if (isViewOnce) "📸 [VIEW ONCE] $content" else content
                    val message = Message(
                        sender = sender,
                        content = messageText,
                        packageName = packageName,
                        timestamp = System.currentTimeMillis(),
                        isDeleted = false
                    )
                    db.messageDao().insertMessage(message)
                    Log.d(TAG, "Saved: $sender - $messageText")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error saving message: ${e.message}")
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "WhatsApp Logger Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Service for logging WhatsApp messages"
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager?.createNotificationChannel(channel)
        }
    }

    private fun createForegroundNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("WhatsApp Logger")
            .setContentText("Monitoring notifications...")
            .setSmallIcon(android.R.drawable.ic_menu_save)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::mediaInterceptor.isInitialized) {
            mediaInterceptor.stopWatching()
        }
    }
}
