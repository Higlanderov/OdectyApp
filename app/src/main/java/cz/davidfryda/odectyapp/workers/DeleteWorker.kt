package cz.davidfryda.odectyapp.workers

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.ktx.storage
import cz.davidfryda.odectyapp.data.Reading
import cz.davidfryda.odectyapp.database.AppDatabase
import cz.davidfryda.odectyapp.database.DeletionTypes
import kotlinx.coroutines.tasks.await
import java.net.URLDecoder

class DeleteWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    private val db = Firebase.firestore
    private val storage = Firebase.storage
    private val pendingDeletionDao = AppDatabase.getDatabase(appContext).pendingDeletionDao()

    companion object {
        const val TAG = "DeleteWorker"
    }

    override suspend fun doWork(): Result {
        Log.d(TAG, "DeleteWorker spuštěn.")

        val pendingDeletions = pendingDeletionDao.getAllPendingDeletions()
        if (pendingDeletions.isEmpty()) {
            Log.d(TAG, "Žádné položky k mazání.")
            return Result.success()
        }

        Log.d(TAG, "Nalezeno ${pendingDeletions.size} položek ke smazání.")

        for (deletion in pendingDeletions) {
            try {
                when (deletion.entityType) {
                    DeletionTypes.LOCATION -> {
                        performLocationCascadeDelete(deletion.userId, deletion.entityId)
                    }
                    DeletionTypes.METER -> {
                        performMeterCascadeDelete(deletion.userId, deletion.entityId)
                    }
                }

                // Pokud vše proběhlo, smažeme úkol z fronty
                pendingDeletionDao.deleteById(deletion.id)
                Log.d(TAG, "Úkol ${deletion.id} (${deletion.entityType}) úspěšně dokončen a smazán z fronty.")

            } catch (e: Exception) {
                Log.e(TAG, "Smazání úkolu ${deletion.id} selhalo: ${e.message}", e)
                // Pokud selže (např. chybí oprávnění), necháme ho ve frontě a zkusíme to příště
                return Result.retry()
            }
        }

        Log.d(TAG, "Všechny úkoly mazání dokončeny.")
        return Result.success()
    }

    /**
     * Kaskádové mazání pro LOKACI.
     */
    private suspend fun performLocationCascadeDelete(userId: String, locationId: String) {
        Log.d(TAG, "Provádím kaskádové mazání lokace: $locationId pro uživatele $userId")

        val batch = db.batch()

        // Krok 1: Najdi všechny měřáky
        val metersRef = db.collection("users").document(userId).collection("meters")
        val metersSnapshot = metersRef.whereEqualTo("locationId", locationId).get().await()
        Log.d(TAG, "Nalezeno ${metersSnapshot.size()} měřáků.")

        // Krok 2: Projdi měřáky
        for (meterDoc in metersSnapshot.documents) {
            // ✨ OPRAVA: Volání sdílené funkce pro úklid odečtů a fotek
            deleteReadingsAndPhotosForMeter(meterDoc.id, batch)

            // Přidání měřáku do dávky
            batch.delete(meterDoc.reference)
        }

        // Krok 3: Přidej lokaci do dávky
        val locationRef = db.collection("users").document(userId).collection("locations").document(locationId)
        batch.delete(locationRef)

        // Krok 4: Spusť dávku
        batch.commit().await()
        Log.d(TAG, "Dávka pro smazání lokace $locationId dokončena.")
    }

    // ✨
    // ✨ CHYBĚJÍCÍ KÓD ZAČÍNÁ ZDE
    // ✨

    /**
     * Kaskádové mazání pro MĚŘÁK.
     */
    private suspend fun performMeterCascadeDelete(userId: String, meterId: String) {
        Log.d(TAG, "Provádím kaskádové mazání měřáku: $meterId pro uživatele $userId")
        val batch = db.batch()

        // Krok 1: Smaž všechny přiřazené odečty a fotky
        deleteReadingsAndPhotosForMeter(meterId, batch)

        // Krok 2: Smaž samotný měřák
        val meterRef = db.collection("users").document(userId).collection("meters").document(meterId)
        batch.delete(meterRef)

        // Krok 3: Spusť dávku
        batch.commit().await()
        Log.d(TAG, "Dávka pro smazání měřáku $meterId dokončena.")
    }


    /**
     * Sdílená pomocná funkce, která smaže všechny odečty a fotky přiřazené k danému meterId.
     */
    private suspend fun deleteReadingsAndPhotosForMeter(meterId: String, batch: com.google.firebase.firestore.WriteBatch) {
        Log.d(TAG, "Hledám odečty a fotky pro měřák $meterId")

        // Najdi odečty
        val readingsRef = db.collection("readings")
        val readingsSnapshot = readingsRef.whereEqualTo("meterId", meterId).get().await()
        Log.d(TAG, "Nalezeno ${readingsSnapshot.size()} odečtů.")

        // Projdi odečty
        for (readingDoc in readingsSnapshot.documents) {
            val reading = readingDoc.toObject(Reading::class.java)

            // Mazání fotky (používáme 'photoUrl')
            val photoUrl = reading?.photoUrl
            if (photoUrl != null && photoUrl.isNotBlank()) {
                try {
                    // Zkusíme extrahovat cestu z URL
                    var pathToDelete = photoUrl.substringAfter("/o/").substringBefore("?alt=media")
                    pathToDelete = URLDecoder.decode(pathToDelete, "UTF-8")
                    storage.reference.child(pathToDelete).delete().await()
                    Log.d(TAG, "Fotka smazána ze Storage (z URL): $pathToDelete")
                } catch (_: Exception) {
                    // Pokud to selže (není to URL), zkusíme to jako přímou cestu
                    try {
                        val photoRef = storage.reference.child(photoUrl)
                        photoRef.delete().await()
                        Log.d(TAG, "Fotka smazána ze Storage (jako přímá cesta): $photoUrl")
                    } catch (e2: Exception) {
                        Log.w(TAG, "Fotku $photoUrl se nepodařilo smazat (možná neexistuje): ${e2.message}")
                    }
                }
            }

            // Přidání odečtu do dávky ke smazání
            batch.delete(readingDoc.reference)
        }
    }
}