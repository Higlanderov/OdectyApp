package cz.davidfryda.odectyapp

import android.app.Application
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

class OdectyApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        setupRecurringWork()
    }

    private fun setupRecurringWork() {
        val workRequest = PeriodicWorkRequestBuilder<NotificationWorker>(
            1, TimeUnit.DAYS // Spustit jednou denně
        ).build()

        WorkManager.getInstance(applicationContext).enqueueUniquePeriodicWork(
            "ReadingReminderWork",
            ExistingPeriodicWorkPolicy.KEEP, // Pokud už práce existuje, nechat ji běžet
            workRequest
        )
    }
}
