package cz.davidfryda.odectyapp

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class MyFirebaseMessagingService : FirebaseMessagingService() {

    companion object {
        private const val TAG = "FCMService"
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        Log.d(TAG, "=== FCM Message Received ===")
        Log.d(TAG, "From: ${remoteMessage.from}")

        // Zpracuj data payload
        if (remoteMessage.data.isNotEmpty()) {
            Log.d(TAG, "Message data payload: ${remoteMessage.data}")

            val type = remoteMessage.data["type"]
            Log.d(TAG, "Notification type: $type")

            when (type) {
                "new_reading" -> {
                    Log.d(TAG, "Handling NEW_READING notification")
                    showNewReadingNotification(remoteMessage)
                }
                "user_registered" -> {
                    Log.d(TAG, "Handling USER_REGISTERED notification")
                    showNewUserNotification(remoteMessage)
                }
                "MONTHLY_REMINDER" -> {
                    Log.d(TAG, "Handling MONTHLY_REMINDER notification")
                    showMonthlyReminderNotification(remoteMessage)
                }
                else -> {
                    Log.d(TAG, "Unknown notification type: $type")
                    remoteMessage.notification?.let {
                        showGenericNotification(it.title, it.body)
                    }
                }
            }
        }

        // Zpracuj notification payload (fallback pokud není data)
        remoteMessage.notification?.let {
            Log.d(TAG, "Message Notification - Title: ${it.title}, Body: ${it.body}")
        }
    }

    private fun showMonthlyReminderNotification(remoteMessage: RemoteMessage) {
        Log.d(TAG, "Creating monthly reminder notification")

        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("OPEN_FRAGMENT", "MAIN")
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val title = remoteMessage.notification?.title ?: "Čas na odečet! 📊"
        val body = remoteMessage.notification?.body ?: "Nezapomeňte odeslat měsíční odečty"

        val notification = NotificationCompat.Builder(this, "monthly_reminders")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText(body))
            .setVibrate(longArrayOf(0, 500, 250, 500))
            .build()

        val notificationId = System.currentTimeMillis().toInt()
        notificationManager.notify(notificationId, notification)

        Log.d(TAG, "✅ Monthly reminder notification displayed (ID: $notificationId)")
    }

    private fun showNewReadingNotification(remoteMessage: RemoteMessage) {
        Log.d(TAG, "Creating new reading notification for master")

        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("OPEN_FRAGMENT", "NOTIFICATIONS")
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            1,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val title = remoteMessage.notification?.title ?: "Nový odečet"
        val body = remoteMessage.notification?.body ?: "Uživatel přidal nový odečet"

        val notification = NotificationCompat.Builder(this, "new_readings")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText(body))
            .setVibrate(longArrayOf(0, 300, 200, 300))
            .build()

        val notificationId = System.currentTimeMillis().toInt()
        notificationManager.notify(notificationId, notification)

        Log.d(TAG, "✅ New reading notification displayed (ID: $notificationId)")
    }

    private fun showNewUserNotification(remoteMessage: RemoteMessage) {
        Log.d(TAG, "Creating new user registration notification for master")

        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("OPEN_FRAGMENT", "NOTIFICATIONS")
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            2,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val title = remoteMessage.notification?.title ?: "Nová registrace"
        val body = remoteMessage.notification?.body ?: "Nový uživatel se zaregistroval"

        val notification = NotificationCompat.Builder(this, "new_readings")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText(body))
            .setVibrate(longArrayOf(0, 300, 200, 300))
            .build()

        val notificationId = System.currentTimeMillis().toInt()
        notificationManager.notify(notificationId, notification)

        Log.d(TAG, "✅ New user notification displayed (ID: $notificationId)")
    }

    private fun showGenericNotification(title: String?, body: String?) {
        Log.d(TAG, "Creating generic notification")

        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            3,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(this, "monthly_reminders")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title ?: "Nová notifikace")
            .setContentText(body ?: "")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        val notificationId = System.currentTimeMillis().toInt()
        notificationManager.notify(notificationId, notification)

        Log.d(TAG, "✅ Generic notification displayed (ID: $notificationId)")
    }

    override fun onNewToken(token: String) {
        Log.d(TAG, "🔄 FCM Token refreshed: ${token.take(20)}...")
        sendTokenToServer(token)
    }

    @Suppress("UNUSED_PARAMETER")
    private fun sendTokenToServer(token: String) {
        Log.d(TAG, "Token bude uložen při příštím spuštění MainActivity")
    }
}