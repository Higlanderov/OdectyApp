package cz.davidfryda.odectyapp.ui.main

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.ktx.storage
import cz.davidfryda.odectyapp.data.Meter
import cz.davidfryda.odectyapp.data.Reading
import cz.davidfryda.odectyapp.database.AppDatabase
import cz.davidfryda.odectyapp.database.OfflineReading
import cz.davidfryda.odectyapp.workers.UploadWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.Date
import java.util.Locale
import kotlin.math.pow
import kotlin.math.sqrt

class MeterDetailViewModel : ViewModel() {
    private val db = Firebase.firestore
    private val storage = Firebase.storage
    private val tag = "MeterDetailViewModel"

    private val _uploadResult = MutableLiveData<UploadResult>(UploadResult.Idle)
    val uploadResult: LiveData<UploadResult> = _uploadResult

    private val _meter = MutableLiveData<Meter>()
    val meter: LiveData<Meter> = _meter

    private val _singleReading = MutableLiveData<Reading?>()
    val singleReading: LiveData<Reading?> = _singleReading

    private val _updateResult = MutableLiveData<UploadResult>(UploadResult.Idle)
    val updateResult: LiveData<UploadResult> = _updateResult

    private val _validationResult = MutableLiveData<ValidationResult>()
    val validationResult: LiveData<ValidationResult> = _validationResult

    private val _deleteResult = MutableLiveData<UploadResult>(UploadResult.Idle)
    val deleteResult: LiveData<UploadResult> = _deleteResult

    lateinit var readingHistory: LiveData<List<Reading>>
        private set

    fun initializeForUser(userId: String, meterId: String, context: Context) {
        val dao = AppDatabase.getDatabase(context).readingDao()
        val offlineFlow = dao.getOfflineReadingsForUser(userId)
        val onlineFlow = MutableStateFlow<List<Reading>>(emptyList())

        db.collection("readings")
            .whereEqualTo("userId", userId)
            .whereEqualTo("meterId", meterId)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshots, error ->
                if (error != null) {
                    Log.e(tag, "initializeForUser: Chyba při načítání online odečtů", error)
                    onlineFlow.value = emptyList()
                    return@addSnapshotListener
                }
                val readings = snapshots?.map { doc ->
                    doc.toObject(Reading::class.java).copy(id = doc.id, isSynced = true)
                } ?: emptyList()
                onlineFlow.value = readings
            }

        readingHistory = offlineFlow.combine(onlineFlow) { offline, online ->
            val offlineConverted = offline.map {
                Reading(id = "offline_${it.id}", meterId = it.meterId, userId = it.userId,
                    finalValue = it.finalValue, timestamp = Date(it.timestamp), isSynced = false)
            }
            (offlineConverted + online).sortedByDescending { it.timestamp }
        }.asLiveData()
    }

    // ✨ UPRAVENO: Nová validace s pokročilou statistikou
    fun validateAndSaveReading(userId: String, meterId: String, photoUri: Uri, manualValue: Double, context: Context) {
        viewModelScope.launch {
            _uploadResult.value = UploadResult.Loading
            try {
                // 1️⃣ Načti poslední odečty (až 5 pro lepší statistiku)
                val recentReadingsSnapshot = db.collection("readings")
                    .whereEqualTo("userId", userId)
                    .whereEqualTo("meterId", meterId)
                    .orderBy("timestamp", Query.Direction.DESCENDING)
                    .limit(5)
                    .get()
                    .await()

                val recentReadings = recentReadingsSnapshot.documents
                    .mapNotNull { it.toObject(Reading::class.java) }
                    .sortedByDescending { it.timestamp }

                Log.d(tag, "validateAndSaveReading: Načteno ${recentReadings.size} historických odečtů")

                // 2️⃣ Pokud neexistují předchozí odečty, ulož rovnou
                if (recentReadings.isEmpty() || recentReadings.first().finalValue == null) {
                    Log.d(tag, "validateAndSaveReading: První odečet, ukládám přímo")
                    forceSaveReading(userId, meterId, photoUri, manualValue, context)
                    return@launch
                }

                val lastValue = recentReadings.first().finalValue!!

                // 3️⃣ PODMÍNKA 1: Nová hodnota je NIŽŠÍ než poslední
                if (manualValue < lastValue) {
                    Log.d(tag, "validateAndSaveReading: Hodnota je nižší než poslední ($manualValue < $lastValue)")
                    _uploadResult.value = UploadResult.Success
                    _validationResult.value = ValidationResult.WarningLow(
                        "Nová hodnota ($manualValue) je nižší než poslední odečet ($lastValue). Opravdu chcete pokračovat?"
                    )
                    return@launch
                }

                // 4️⃣ NOVÁ LOGIKA: Pokročilá validace proti historii
                val validationResult = if (recentReadings.size >= 3) {
                    Log.d(tag, "validateAndSaveReading: Použita pokročilá statistická validace")
                    validateAgainstHistoryAdvanced(manualValue, recentReadings)
                } else {
                    Log.d(tag, "validateAndSaveReading: Použita jednoduchá validace (málo dat)")
                    validateAgainstHistorySimple(manualValue, recentReadings)
                }

                when (validationResult) {
                    is ValidationResult.WarningHigh -> {
                        _uploadResult.value = UploadResult.Success
                        _validationResult.value = validationResult
                    }
                    is ValidationResult.Valid -> {
                        forceSaveReading(userId, meterId, photoUri, manualValue, context)
                    }
                    else -> {
                        _uploadResult.value = UploadResult.Error("Neočekávaný výsledek validace")
                    }
                }

            } catch (e: Exception) {
                Log.e(tag, "Chyba při validaci.", e)
                _uploadResult.value = UploadResult.Error(e.message ?: "Chyba při validaci.")
            }
        }
    }

    // 🆕 NOVÁ METODA: Pokročilá statistická validace (Z-score)
    private fun validateAgainstHistoryAdvanced(newValue: Double, recentReadings: List<Reading>): ValidationResult {
        // KONTROLA 1: Potřebujeme alespoň 3 odečty pro statistiku
        if (recentReadings.size < 3) {
            Log.d(tag, "validateAdvanced: Málo dat (${recentReadings.size} odečtů). Použit fallback.")
            return validateAgainstHistorySimple(newValue, recentReadings)
        }

        // Výpočet rozdílů mezi po sobě jdoucími odečty
        val differences = mutableListOf<Double>()
        for (i in 0 until recentReadings.size - 1) {
            val current = recentReadings[i].finalValue
            val previous = recentReadings[i + 1].finalValue
            if (current != null && previous != null && current > previous) {
                differences.add(current - previous)
            }
        }

        // KONTROLA 2: Potřebujeme alespoň 2 rozdíly
        if (differences.size < 2) {
            Log.d(tag, "validateAdvanced: Málo rozdílů (${differences.size}). Použit fallback.")
            return validateAgainstHistorySimple(newValue, recentReadings)
        }

        // Výpočet statistických hodnot
        val mean = differences.average()
        val variance = differences.map { (it - mean).pow(2) }.average()
        val standardDeviation = sqrt(variance)

        val lastValue = recentReadings.first().finalValue!!
        val currentIncrease = newValue - lastValue

        Log.d(tag, "=== Pokročilá validace ===")
        Log.d(tag, "Počet odečtů: ${recentReadings.size}")
        Log.d(tag, "Poslední hodnoty: ${recentReadings.map { it.finalValue }}")
        Log.d(tag, "Rozdíly: $differences")
        Log.d(tag, "Průměr: $mean")
        Log.d(tag, "Směrodatná odchylka: $standardDeviation")
        Log.d(tag, "Aktuální nárůst: $currentIncrease")

        // KONTROLA 3: Směrodatná odchylka je příliš malá (téměř konstantní spotřeba)
        if (standardDeviation < 1.0) {
            Log.d(tag, "validateAdvanced: Směrodatná odchylka příliš malá ($standardDeviation). Použit jednodušší výpočet.")
            // Pokud je spotřeba téměř konstantní, použijeme toleranci 50%
            return if (currentIncrease > mean * 1.5) {
                ValidationResult.WarningHigh(
                    "Spotřeba (${String.format(Locale.getDefault(), "%.1f", currentIncrease)}) je výrazně vyšší než obvykle " +
                            "(průměr: ${String.format(Locale.getDefault(), "%.1f", mean)}). Je hodnota správně?"
                )
            } else {
                ValidationResult.Valid
            }
        }

        // Z-score: Kolik směrodatných odchylek je hodnota od průměru
        val zScore = (currentIncrease - mean) / standardDeviation

        Log.d(tag, "Z-score: $zScore")

        // PRAVIDLA VALIDACE
        return when {
            zScore > 3 -> {
                Log.d(tag, "validateAdvanced: Z-score > 3 → EXTRÉMNÍ ANOMÁLIE")
                ValidationResult.WarningHigh(
                    "Spotřeba je mimořádně vysoká (${String.format(Locale.getDefault(), "%.1f", currentIncrease)} vs průměr ${String.format(Locale.getDefault(), "%.1f", mean)}). " +
                            "Zkontrolujte prosím odečet!"
                )
            }
            zScore > 2 -> {
                Log.d(tag, "validateAdvanced: Z-score > 2 → NEOBVYKLÁ HODNOTA")
                ValidationResult.WarningHigh(
                    "Spotřeba je neobvykle vysoká (${String.format(Locale.getDefault(), "%.1f", currentIncrease)} vs průměr ${String.format(Locale.getDefault(), "%.1f", mean)}). " +
                            "Je hodnota správně?"
                )
            }
            else -> {
                Log.d(tag, "validateAdvanced: Z-score OK → VALIDNÍ")
                ValidationResult.Valid
            }
        }
    }

    // 🆕 NOVÁ METODA: Jednoduchá validace pro málo dat
    private fun validateAgainstHistorySimple(newValue: Double, recentReadings: List<Reading>): ValidationResult {
        if (recentReadings.isEmpty()) {
            return ValidationResult.Valid
        }

        val lastValue = recentReadings.first().finalValue ?: return ValidationResult.Valid

        Log.d(tag, "=== Jednoduchá validace ===")
        Log.d(tag, "Počet odečtů: ${recentReadings.size}")
        Log.d(tag, "Poslední hodnota: $lastValue")
        Log.d(tag, "Nová hodnota: $newValue")

        // Pokud máme jen 1 historický záznam, použijeme pevný práh 100%
        if (recentReadings.size == 1) {
            Log.d(tag, "validateSimple: Jen 1 odečet → použit pevný práh 100%")
            return if (newValue > lastValue * 2) {
                ValidationResult.WarningHigh(
                    "Nová hodnota ($newValue) je o více než 100% vyšší než poslední odečet ($lastValue). Jste si jistý/á?"
                )
            } else {
                ValidationResult.Valid
            }
        }

        // Pokud máme 2+ odečty, zkusíme jednoduchý průměr
        val differences = mutableListOf<Double>()
        for (i in 0 until recentReadings.size - 1) {
            val current = recentReadings[i].finalValue
            val previous = recentReadings[i + 1].finalValue
            if (current != null && previous != null && current > previous) {
                differences.add(current - previous)
            }
        }

        if (differences.isEmpty()) {
            Log.d(tag, "validateSimple: Žádné rozdíly → fallback na pevný práh")
            // Fallback na pevný práh
            return if (newValue > lastValue * 2) {
                ValidationResult.WarningHigh(
                    "Nová hodnota ($newValue) je o více než 100% vyšší než poslední odečet ($lastValue). Jste si jistý/á?"
                )
            } else {
                ValidationResult.Valid
            }
        }

        // Máme alespoň 1 rozdíl, použijeme toleranci 3× průměr
        val averageIncrease = differences.average()
        val currentIncrease = newValue - lastValue

        Log.d(tag, "validateSimple: Rozdíly: $differences")
        Log.d(tag, "validateSimple: Průměrná spotřeba: $averageIncrease")
        Log.d(tag, "validateSimple: Aktuální nárůst: $currentIncrease")

        return if (currentIncrease > averageIncrease * 3) {
            val percentageIncrease = ((currentIncrease / averageIncrease - 1) * 100).toInt()
            Log.d(tag, "validateSimple: Nárůst ${percentageIncrease}% nad průměrem → VAROVÁNÍ")
            ValidationResult.WarningHigh(
                "Spotřeba je ${percentageIncrease}% vyšší než Váš průměr (${String.format(Locale.getDefault(), "%.1f", averageIncrease)}). " +
                        "Aktuální nárůst: ${String.format(Locale.getDefault(), "%.1f", currentIncrease)}. Zkontrolujte prosím hodnotu."
            )
        } else {
            Log.d(tag, "validateSimple: V toleranci → VALIDNÍ")
            ValidationResult.Valid
        }
    }

    fun forceSaveReading(userId: String, meterId: String, photoUri: Uri, manualValue: Double, context: Context) {
        if (isOnline(context)) {
            saveReadingOnline(userId, meterId, photoUri, manualValue)
        } else {
            saveReadingOffline(userId, meterId, photoUri, manualValue, context)
        }
    }

    private fun saveReadingOnline(userId: String, meterId: String, photoUri: Uri, manualValue: Double) {
        viewModelScope.launch {
            try {
                val photoFileName = "${System.currentTimeMillis()}.jpg"
                val photoRef = storage.reference.child("readings/$userId/$meterId/$photoFileName")
                photoRef.putFile(photoUri).await()
                val downloadUrl = photoRef.downloadUrl.await().toString()
                val readingData = hashMapOf(
                    "timestamp" to FieldValue.serverTimestamp(),
                    "photoUrl" to downloadUrl, "status" to "hotovo", "finalValue" to manualValue,
                    "meterId" to meterId, "userId" to userId, "editedByAdmin" to false
                )
                db.collection("readings").add(readingData).await()
                _uploadResult.value = UploadResult.Success
                Log.d(tag, "saveReadingOnline: Odečet úspěšně nahrán.")
            } catch (e: Exception) {
                Log.e(tag, "Chyba při nahrávání online.", e)
                _uploadResult.value = UploadResult.Error(e.message ?: "Chyba při nahrávání.")
            }
        }
    }

    private fun saveReadingOffline(userId: String, meterId: String, photoUri: Uri, manualValue: Double, context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val inputStream = context.contentResolver.openInputStream(photoUri)
                val localFile = File(context.filesDir, "offline_${System.currentTimeMillis()}.jpg")
                val outputStream = FileOutputStream(localFile)
                inputStream?.copyTo(outputStream)
                inputStream?.close(); outputStream.close()
                Log.d(tag, "saveReadingOffline: Fotografie uložena lokálně: ${localFile.absolutePath}")

                val offlineReading = OfflineReading(userId = userId, meterId = meterId,
                    localPhotoPath = localFile.absolutePath, finalValue = manualValue)
                val dao = AppDatabase.getDatabase(context).readingDao()
                val newId = dao.insert(offlineReading)
                Log.d(tag, "saveReadingOffline: Záznam vložen do Room s ID: $newId")

                val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
                val uploadWorkRequest = OneTimeWorkRequestBuilder<UploadWorker>()
                    .setConstraints(constraints)
                    .setInputData(workDataOf("offline_reading_id" to newId))
                    .build()
                WorkManager.getInstance(context).enqueue(uploadWorkRequest)
                Log.d(tag, "saveReadingOffline: WorkManager naplánován pro ID: $newId")

                _uploadResult.postValue(UploadResult.Success)
            } catch (e: Exception) {
                Log.e(tag, "Chyba při ukládání pro offline použití.", e)
                _uploadResult.postValue(UploadResult.Error(e.message ?: "Chyba při ukládání pro offline použití."))
            }
        }
    }

    private fun isOnline(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val activeNetwork = connectivityManager.getNetworkCapabilities(network) ?: return false
        return when {
            activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> true
            activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> true
            else -> false
        }
    }

    // ✨✨✨ KLÍČOVÁ OPRAVA ZDE ✨✨✨
    // Funkce nyní přijímá pouze 'userId' a 'meterId', protože 'locationId' není potřeba
    // k nalezení měřáku podle vaší struktury z 'index.js'.
    fun loadMeterDetails(userId: String, meterId: String) {
        db.collection("users").document(userId)
            .collection("meters").document(meterId)    // <-- OPRAVA (podle index.js)
            .get()
            .addOnSuccessListener { document ->
                document.toObject(Meter::class.java)?.let {
                    _meter.value = it.copy(id = document.id)
                }
            }
            .addOnFailureListener { e ->
                Log.e(tag, "loadMeterDetails: Chyba při načítání detailu měřáku $meterId", e)
            }
    }

    fun loadSingleReading(readingId: String) {
        if (readingId.startsWith("offline_")) {
            Log.w(tag, "loadSingleReading: Pokus o načtení detailu pro offline záznam $readingId - není implementováno.")
            _singleReading.value = Reading(id=readingId, isSynced = false)
            return
        }
        db.collection("readings").document(readingId)
            .addSnapshotListener { document, error ->
                if (error != null) {
                    Log.e(tag, "loadSingleReading: Chyba při načítání odečtu $readingId", error)
                    _singleReading.value = null
                    return@addSnapshotListener
                }
                document?.toObject(Reading::class.java)?.let {
                    _singleReading.value = it.copy(id = document.id, isSynced = true)
                } ?: run {
                    _singleReading.value = null
                    Log.w(tag, "loadSingleReading: Dokument odečtu $readingId nenalezen.")
                }
            }
    }

    fun updateReadingValue(readingId: String, newValue: Double, asMaster: Boolean) {
        viewModelScope.launch {
            if (readingId.startsWith("offline_")) {
                Log.e(tag, "updateReadingValue: Nelze upravit offline záznam $readingId.")
                _updateResult.value = UploadResult.Error("Nelze upravit offline záznam.")
                return@launch
            }
            _updateResult.value = UploadResult.Loading
            try {
                val updateMap = mutableMapOf<String, Any>("finalValue" to newValue)
                if (asMaster) {
                    updateMap["editedByAdmin"] = true
                }
                db.collection("readings").document(readingId)
                    .update(updateMap)
                    .await()
                _updateResult.value = UploadResult.Success
                Log.d(tag, "updateReadingValue: Hodnota odečtu $readingId aktualizována.")
            } catch (e: Exception) {
                Log.e(tag, "Chyba při aktualizaci hodnoty odečtu $readingId.", e)
                _updateResult.value = UploadResult.Error(e.message ?: "Chyba při aktualizaci.")
            }
        }
    }

    fun deleteReading(readingId: String, photoUrl: String?, context: Context) {
        viewModelScope.launch {
            Log.d(tag, "=== deleteReading called ===")
            Log.d(tag, "readingId: $readingId")
            Log.d(tag, "photoUrl: '$photoUrl'")

            // Kontrola offline ID
            if (readingId.startsWith("offline_")) {
                _deleteResult.value = UploadResult.Loading
                try {
                    val dao = AppDatabase.getDatabase(context).readingDao()
                    val offlineId = readingId.removePrefix("offline_").toIntOrNull()
                    if (offlineId != null) {
                        val offlineReading = dao.getById(offlineId)
                        dao.deleteById(offlineId)
                        offlineReading?.localPhotoPath?.let { path ->
                            withContext(Dispatchers.IO) {
                                try {
                                    File(path).delete()
                                    Log.d(tag, "deleteReading: Lokální fotka smazána: $path")
                                } catch (e: Exception) {
                                    Log.e(tag, "deleteReading: Chyba při mazání lokální fotky $path", e)
                                }
                            }
                        }
                        Log.d(tag, "deleteReading: Offline odečet $readingId smazán z Room.")
                        _deleteResult.value = UploadResult.Success
                    } else {
                        throw IllegalArgumentException("Neplatné offline ID: $readingId")
                    }
                } catch (e: Exception) {
                    Log.e(tag, "Chyba při mazání offline odečtu $readingId.", e)
                    _deleteResult.value = UploadResult.Error(e.message ?: "Chyba při mazání offline odečtu.")
                }
                return@launch
            }

            // Online mazání - NEJDŘÍV fotka, PAK dokument
            _deleteResult.value = UploadResult.Loading
            try {
                // 1. NEJDŘÍV smazat fotku ze Storage (pokud existuje URL)
                if (!photoUrl.isNullOrEmpty()) {
                    Log.d(tag, "deleteReading: Začínám mazat fotku ze Storage...")
                    withContext(Dispatchers.IO) {
                        try {
                            val photoRef = storage.getReferenceFromUrl(photoUrl)
                            Log.d(tag, "deleteReading: Storage path: ${photoRef.path}")

                            photoRef.delete().await()
                            Log.d(tag, "deleteReading: ✅ Fotka ÚSPĚŠNĚ smazána ze Storage.")
                        } catch (e: Exception) {
                            Log.e(tag, "deleteReading: ❌ CHYBA při mazání fotky ze Storage", e)
                            // Pokračujeme i při chybě, abychom mohli smazat alespoň dokument
                        }
                    }
                } else {
                    Log.w(tag, "deleteReading: photoUrl je NULL nebo prázdné")
                }

                // 2. TEPRVE PAK smazat dokument z Firestore
                Log.d(tag, "deleteReading: Mazání dokumentu z Firestore...")
                db.collection("readings").document(readingId).delete().await()
                Log.d(tag, "deleteReading: Odečet $readingId smazán z Firestore.")

                _deleteResult.value = UploadResult.Success

            } catch (e: Exception) {
                Log.e(tag, "deleteReading: Chyba při mazání odečtu $readingId.", e)
                _deleteResult.value = UploadResult.Error(e.message ?: "Chyba při mazání odečtu.")
            }
        }
    }

    fun resetDeleteResult() {
        _deleteResult.value = UploadResult.Idle
        Log.d(tag,"resetDeleteResult: Stav resetován na Idle.")
    }

    fun resetUpdateResult() {
        _updateResult.value = UploadResult.Idle
        Log.d(tag,"resetUpdateResult: Stav resetován na Idle.")
    }

    fun resetUploadResult() {
        _uploadResult.value = UploadResult.Idle
        _validationResult.value = ValidationResult.Valid
        Log.d(tag,"resetUploadResult: Stav resetován na Idle.")
    }

    fun resetValidationResult() {
        _validationResult.value = ValidationResult.Valid
        Log.d(tag, "resetValidationResult: Stav resetován na Valid.")
    }
}