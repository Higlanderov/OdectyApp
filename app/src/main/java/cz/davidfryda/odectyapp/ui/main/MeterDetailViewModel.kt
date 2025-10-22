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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.io.File
import java.io.FileOutputStream
import java.util.Date

class MeterDetailViewModel : ViewModel() {
    private val db = Firebase.firestore
    private val storage = Firebase.storage

    private val _uploadResult = MutableLiveData<UploadResult>()
    val uploadResult: LiveData<UploadResult> = _uploadResult

    private val _meter = MutableLiveData<Meter>()
    val meter: LiveData<Meter> = _meter

    private val _singleReading = MutableLiveData<Reading>()
    val singleReading: LiveData<Reading> = _singleReading

    private val _updateResult = MutableLiveData<UploadResult>()
    val updateResult: LiveData<UploadResult> = _updateResult

    private val _validationResult = MutableLiveData<ValidationResult>()
    val validationResult: LiveData<ValidationResult> = _validationResult

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
            .addSnapshotListener { snapshots, _ ->
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

    fun validateAndSaveReading(userId: String, meterId: String, photoUri: Uri, manualValue: Double, context: Context) {
        viewModelScope.launch {
            _uploadResult.value = UploadResult.Loading
            try {
                val lastReadingSnapshot = db.collection("readings")
                    .whereEqualTo("userId", userId)
                    .whereEqualTo("meterId", meterId)
                    .orderBy("timestamp", Query.Direction.DESCENDING)
                    .limit(1)
                    .get()
                    .await()

                val lastReading = lastReadingSnapshot.documents.firstOrNull()?.toObject(Reading::class.java)

                if (lastReading?.finalValue == null) {
                    forceSaveReading(userId, meterId, photoUri, manualValue, context)
                    return@launch
                }

                val lastValue = lastReading.finalValue

                if (manualValue < lastValue) {
                    _uploadResult.value = UploadResult.Success
                    _validationResult.value = ValidationResult.WarningLow("Nová hodnota ($manualValue) je nižší než poslední odečet ($lastValue). Opravdu chcete pokračovat?")
                } else if (manualValue > lastValue * 2 && lastValue > 0) {
                    _uploadResult.value = UploadResult.Success
                    _validationResult.value = ValidationResult.WarningHigh("Nová hodnota ($manualValue) je o více než 100% vyšší než poslední odečet. Jste si jistý/á?")
                } else {
                    forceSaveReading(userId, meterId, photoUri, manualValue, context)
                }
            } catch (e: Exception) {
                Log.e("MeterDetailViewModel", "Chyba při validaci.", e)
                _uploadResult.value = UploadResult.Error(e.message ?: "Chyba při validaci.")
            }
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
            _uploadResult.value = UploadResult.Loading
            try {
                val photoFileName = "${System.currentTimeMillis()}.jpg"
                val photoRef = storage.reference.child("readings/$userId/$photoFileName")
                photoRef.putFile(photoUri).await()
                val downloadUrl = photoRef.downloadUrl.await().toString()
                val readingData = hashMapOf(
                    "timestamp" to FieldValue.serverTimestamp(),
                    "photoUrl" to downloadUrl, "status" to "hotovo", "finalValue" to manualValue,
                    "meterId" to meterId, "userId" to userId, "editedByAdmin" to false
                )
                db.collection("readings").add(readingData).await()
                _uploadResult.value = UploadResult.Success
            } catch (e: Exception) {
                Log.e("MeterDetailViewModel", "Chyba při nahrávání online.", e)
                _uploadResult.value = UploadResult.Error(e.message ?: "Chyba při nahrávání.")
            }
        }
    }

    private fun saveReadingOffline(userId: String, meterId: String, photoUri: Uri, manualValue: Double, context: Context) {
        viewModelScope.launch {
            _uploadResult.value = UploadResult.Loading
            try {
                val inputStream = context.contentResolver.openInputStream(photoUri)
                val localFile = File(context.filesDir, "${System.currentTimeMillis()}.jpg")
                val outputStream = FileOutputStream(localFile)
                inputStream?.copyTo(outputStream)
                inputStream?.close(); outputStream.close()

                val offlineReading = OfflineReading(userId = userId, meterId = meterId,
                    localPhotoPath = localFile.absolutePath, finalValue = manualValue)
                val dao = AppDatabase.getDatabase(context).readingDao()
                val newId = dao.insert(offlineReading)

                val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
                val uploadWorkRequest = OneTimeWorkRequestBuilder<UploadWorker>()
                    .setConstraints(constraints)
                    .setInputData(workDataOf("offline_reading_id" to newId))
                    .build()
                WorkManager.getInstance(context).enqueue(uploadWorkRequest)
                _uploadResult.value = UploadResult.Success
            } catch (e: Exception) {
                Log.e("MeterDetailViewModel", "Chyba při ukládání pro offline použití.", e)
                _uploadResult.value = UploadResult.Error("Chyba při ukládání pro offline použití.")
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

    fun loadMeterDetails(userId: String, meterId: String) {
        db.collection("users").document(userId).collection("meters").document(meterId)
            .get()
            .addOnSuccessListener { document ->
                document.toObject(Meter::class.java)?.let {
                    _meter.value = it.copy(id = document.id)
                }
            }
    }

    fun loadSingleReading(readingId: String) {
        db.collection("readings").document(readingId)
            .addSnapshotListener { document, _ ->
                document?.toObject(Reading::class.java)?.let {
                    _singleReading.value = it.copy(id = document.id)
                }
            }
    }

    fun updateReadingValue(readingId: String, newValue: Double, asMaster: Boolean) {
        viewModelScope.launch {
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
            } catch (e: Exception) {
                Log.e("MeterDetailViewModel", "Chyba při aktualizaci.", e)
                _updateResult.value = UploadResult.Error(e.message ?: "Chyba při aktualizaci.")
            }
        }
    }
}
