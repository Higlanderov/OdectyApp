package cz.davidfryda.odectyapp

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.tasks.await
import java.util.Calendar

class NotificationWorker(appContext: Context, workerParams: WorkerParameters) :
    CoroutineWorker(appContext, workerParams) {

    private val db = Firebase.firestore
    private val auth = Firebase.auth
    private val NOTIFICATION_CHANNEL_ID = "reading_reminder_channel"
    private val NOTIFICATION_ID = 101

    override suspend fun doWork(): Result {
        val currentUser = auth.currentUser ?: return Result.success()

        val calendar = Calendar.getInstance()
        val dayOfMonth = calendar.get(Calendar.DAY_OF_MONTH)
        val month = calendar.get(Calendar.MONTH)
        val year = calendar.get(Calendar.YEAR)

        if (dayOfMonth < 25) {
            return Result.success()
        }

        try {
            val startOfMonth = Calendar.getInstance().apply {
                set(year, month, 1, 0, 0, 0)
            }.time

            val readingsThisMonth = db.collection("readings")
                .whereEqualTo("userId", currentUser.uid)
                .whereGreaterThanOrEqualTo("timestamp", startOfMonth)
                .limit(1)
                .get()
                .await()

            if (readingsThisMonth.isEmpty) {
                showNotification()
            }

        } catch (_: Exception) {
            return Result.failure()
        }

        return Result.success()
    }

    private fun showNotification() {
        createNotificationChannel()

        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent: PendingIntent = PendingIntent.getActivity(
            applicationContext,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(applicationContext, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Připomínka Odečtu")
            .setContentText("Je čas provést měsíční odečet měřáků.")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        if (ActivityCompat.checkSelfPermission(
                applicationContext,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        NotificationManagerCompat.from(applicationContext).notify(NOTIFICATION_ID, builder.build())
    }

    private fun createNotificationChannel() {
        val name = "Připomínky odečtů"
        val descriptionText = "Kanál pro zasílání připomínek k provedení odečtu."
        val importance = NotificationManager.IMPORTANCE_DEFAULT
        val channel = NotificationChannel(NOTIFICATION_CHANNEL_ID, name, importance).apply {
            description = descriptionText
        }
        val notificationManager: NotificationManager =
            applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }
}