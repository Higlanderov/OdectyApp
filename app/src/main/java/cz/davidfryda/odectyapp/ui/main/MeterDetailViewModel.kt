package cz.davidfryda.odectyapp.ui.main

import android.net.Uri
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.ktx.storage
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.UUID

class MeterDetailViewModel : ViewModel() {

    private val db = Firebase.firestore
    private val storage = Firebase.storage

    private val _uploadResult = MutableLiveData<UploadResult>()
    val uploadResult: LiveData<UploadResult> = _uploadResult

    private val _meter = MutableLiveData<Meter>()
    val meter: LiveData<Meter> = _meter

    private val _readingHistory = MutableLiveData<List<Reading>>()
    val readingHistory: LiveData<List<Reading>> = _readingHistory

    private val _singleReading = MutableLiveData<Reading>()
    val singleReading: LiveData<Reading> = _singleReading

    private val _updateResult = MutableLiveData<UploadResult>()
    val updateResult: LiveData<UploadResult> = _updateResult

    fun loadMeterDetails(userId: String, meterId: String) {
        db.collection("users").document(userId).collection("meters").document(meterId)
            .get()
            .addOnSuccessListener { document ->
                document.toObject(Meter::class.java)?.let {
                    _meter.value = it.copy(id = document.id)
                }
            }
    }

    fun loadReadingHistory(userId: String, meterId: String) {
        db.collection("readings")
            .whereEqualTo("userId", userId)
            .whereEqualTo("meterId", meterId)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshots, error ->
                if (error != null || snapshots == null) return@addSnapshotListener
                _readingHistory.value = snapshots.map { doc ->
                    doc.toObject(Reading::class.java).copy(id = doc.id)
                }
            }
    }

    fun saveReading(userId: String, meterId: String, photoUri: Uri, manualValue: Double) {
        viewModelScope.launch {
            _uploadResult.value = UploadResult.Loading
            try {
                val photoFileName = "${System.currentTimeMillis()}.jpg"
                val photoRef = storage.reference.child("readings/$userId/$photoFileName")
                photoRef.putFile(photoUri).await()
                val downloadUrl = photoRef.downloadUrl.await().toString()

                val readingData = hashMapOf(
                    "timestamp" to FieldValue.serverTimestamp(),
                    "photoUrl" to downloadUrl,
                    "status" to "hotovo",
                    "finalValue" to manualValue,
                    "meterId" to meterId,
                    "userId" to userId,
                    "editedByAdmin" to false // Nový záznam nikdy není upraven správcem
                )

                db.collection("readings").add(readingData).await()
                _uploadResult.value = UploadResult.Success
            } catch (e: Exception) {
                _uploadResult.value = UploadResult.Error(e.message ?: "Chyba při nahrávání.")
            }
        }
    }

    fun loadSingleReading(readingId: String) {
        db.collection("readings").document(readingId)
            .addSnapshotListener { document, _ -> // Použijeme addSnapshotListener pro real-time aktualizace
                document?.toObject(Reading::class.java)?.let {
                    _singleReading.value = it.copy(id = document.id)
                }
            }
    }

    // UPRAVENO: Funkce nyní přijímá informaci o tom, kdo ji volá
    fun updateReadingValue(readingId: String, newValue: Double, asMaster: Boolean) {
        viewModelScope.launch {
            _updateResult.value = UploadResult.Loading
            try {
                val updateMap = mutableMapOf<String, Any>("finalValue" to newValue)
                // Pokud úpravu provádí správce, přidáme do mapy i příznak
                if (asMaster) {
                    updateMap["editedByAdmin"] = true
                }

                db.collection("readings").document(readingId)
                    .update(updateMap)
                    .await()
                _updateResult.value = UploadResult.Success
            } catch (e: Exception) {
                _updateResult.value = UploadResult.Error(e.message ?: "Chyba při aktualizaci.")
            }
        }
    }
}
