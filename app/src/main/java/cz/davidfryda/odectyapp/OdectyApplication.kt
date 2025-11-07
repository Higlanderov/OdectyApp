package cz.davidfryda.odectyapp

import android.app.Application
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import java.util.concurrent.TimeUnit

class OdectyApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // === ZAČÁTEK ÚPRAVY ===
        // Povolení offline perzistence pro Firestore.
        // To zajistí, že se data (místa, měřiče) uloží lokálně (do cache)
        // a budou dostupná pro čtení, i když je aplikace offline.
        val settings = FirebaseFirestoreSettings.Builder()
            .setPersistenceEnabled(true)
            .setCacheSizeBytes(FirebaseFirestoreSettings.CACHE_SIZE_UNLIMITED) // Neomezená velikost cache
            .build()
        FirebaseFirestore.getInstance().firestoreSettings = settings
        // === KONEC ÚPRAVY ===

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