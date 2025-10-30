package cz.davidfryda.odectyapp.workers

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.ktx.storage
import cz.davidfryda.odectyapp.database.AppDatabase
import kotlinx.coroutines.tasks.await
import java.io.File

class UploadWorker(appContext: Context, workerParams: WorkerParameters) :
    CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        // OPRAVA: Načteme Long a převedeme ho na Int zde
        val offlineReadingIdLong = inputData.getLong("offline_reading_id", -1L)
        if (offlineReadingIdLong == -1L) return Result.failure()
        val offlineReadingId = offlineReadingIdLong.toInt()

        val dao = AppDatabase.getDatabase(applicationContext).readingDao()
        val offlineReading = dao.getById(offlineReadingId) ?: return Result.failure()

        val db = Firebase.firestore
        val storage = Firebase.storage

        return try {
            // 1. Nahrání fotky ze souboru do Storage
            val file = File(offlineReading.localPhotoPath)
            // OPRAVENO: Přidán meterId do cesty
            val photoRef = storage.reference.child("readings/${offlineReading.userId}/${offlineReading.meterId}/${file.name}")
            photoRef.putFile(Uri.fromFile(file)).await()
            val downloadUrl = photoRef.downloadUrl.await().toString()

            // 2. Příprava a uložení dat do Firestore
            val readingData = hashMapOf(
                "timestamp" to FieldValue.serverTimestamp(),
                "photoUrl" to downloadUrl,
                "status" to "hotovo",
                "finalValue" to offlineReading.finalValue,
                "meterId" to offlineReading.meterId,
                "userId" to offlineReading.userId,
                "editedByAdmin" to false
            )
            db.collection("readings").add(readingData).await()

            // 3. Smazání lokálních dat po úspěchu
            dao.deleteById(offlineReadingId)
            file.delete() // Smažeme i dočasný soubor s fotkou

            Result.success()
        } catch (e: Exception) {
            Log.e("UploadWorker", "Chyba při nahrávání offline odečtu.", e)
            Result.retry()
        }
    }
}
