package cz.davidfryda.odectyapp.ui.location

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import cz.davidfryda.odectyapp.data.Location
import cz.davidfryda.odectyapp.data.Meter
import cz.davidfryda.odectyapp.data.Reading
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.Calendar
import java.util.Date

class LocationDetailViewModel : ViewModel() {
    private val db = Firebase.firestore
    private val tag = "LocationDetailViewModel"

    private val _location = MutableLiveData<Location?>()
    val location: LiveData<Location?> = _location

    private val _meters = MutableLiveData<List<Meter>>()
    val meters: LiveData<List<Meter>> = _meters

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _deleteResult = MutableLiveData<DeleteResult>()
    val deleteResult: LiveData<DeleteResult> = _deleteResult

    private val _deleteMeterResult = MutableLiveData<DeleteMeterResult>()
    val deleteMeterResult: LiveData<DeleteMeterResult> = _deleteMeterResult

    fun loadLocation(userId: String, locationId: String) {
        _isLoading.value = true

        db.collection("users")
            .document(userId)
            .collection("locations")
            .document(locationId)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val location = document.toObject(Location::class.java)?.copy(id = document.id)
                    _location.value = location
                    Log.d(tag, "Location loaded: ${location?.name}")
                } else {
                    Log.e(tag, "Location not found: $locationId")
                    _location.value = null
                }
                _isLoading.value = false
            }
            .addOnFailureListener { e ->
                Log.e(tag, "Error loading location", e)
                _location.value = null
                _isLoading.value = false
            }
    }

    // ✨ UPRAVENÁ METODA: Načítá měřáky včetně posledních odečtů
    fun loadMeters(userId: String, locationId: String) {
        db.collection("users")
            .document(userId)
            .collection("meters")
            .whereEqualTo("locationId", locationId)
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    Log.e(tag, "Error loading meters", e)
                    _meters.value = emptyList()
                    return@addSnapshotListener
                }

                if (snapshot != null) {
                    viewModelScope.launch {
                        val metersList = snapshot.documents.mapNotNull { doc ->
                            doc.toObject(Meter::class.java)?.copy(id = doc.id)
                        }

                        // ✨ NOVÉ: Načíst poslední odečet pro každý měřák
                        val metersWithReadings = metersList.map { meter ->
                            val lastReading = getLastReadingForMeter(userId, meter.id)
                            meter.copy(lastReading = lastReading)
                        }

                        _meters.value = metersWithReadings
                        Log.d(tag, "Loaded ${metersWithReadings.size} meters with readings for location $locationId")
                    }
                } else {
                    _meters.value = emptyList()
                }
            }
    }

    // ✨ NOVÁ METODA: Získá poslední odečet pro měřák
    private suspend fun getLastReadingForMeter(userId: String, meterId: String): Reading? {
        return try {
            val snapshot = db.collection("readings")
                .whereEqualTo("userId", userId)
                .whereEqualTo("meterId", meterId)
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(1)
                .get()
                .await()

            if (!snapshot.isEmpty) {
                snapshot.documents[0].toObject(Reading::class.java)?.copy(
                    id = snapshot.documents[0].id
                )
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(tag, "Error fetching last reading for meter $meterId", e)
            null
        }
    }

    fun deleteLocation(userId: String, locationId: String) {
        viewModelScope.launch {
            try {
                val metersSnapshot = db.collection("users")
                    .document(userId)
                    .collection("meters")
                    .whereEqualTo("locationId", locationId)
                    .limit(1)
                    .get()
                    .await()

                if (!metersSnapshot.isEmpty) {
                    _deleteResult.value = DeleteResult.Error(
                        "Nelze smazat lokaci s měřáky. Nejdřív smažte všechny měřáky."
                    )
                    return@launch
                }

                val locationDoc = db.collection("users")
                    .document(userId)
                    .collection("locations")
                    .document(locationId)
                    .get()
                    .await()

                val isDefault = locationDoc.toObject(Location::class.java)?.isDefault ?: false

                db.collection("users")
                    .document(userId)
                    .collection("locations")
                    .document(locationId)
                    .delete()
                    .await()

                if (isDefault) {
                    val remainingLocations = db.collection("users")
                        .document(userId)
                        .collection("locations")
                        .limit(1)
                        .get()
                        .await()

                    if (!remainingLocations.isEmpty) {
                        val firstLocationId = remainingLocations.documents[0].id
                        db.collection("users")
                            .document(userId)
                            .collection("locations")
                            .document(firstLocationId)
                            .update("isDefault", true)
                            .await()

                        db.collection("users")
                            .document(userId)
                            .update("defaultLocationId", firstLocationId)
                            .await()
                    } else {
                        db.collection("users")
                            .document(userId)
                            .update("defaultLocationId", null)
                            .await()
                    }
                }

                Log.d(tag, "Location deleted: $locationId")
                _deleteResult.value = DeleteResult.Success

            } catch (e: Exception) {
                Log.e(tag, "Error deleting location", e)
                _deleteResult.value = DeleteResult.Error(e.message ?: "Neznámá chyba")
            }
        }
    }

    fun deleteMeter(userId: String, meterId: String) {
        viewModelScope.launch {
            try {
                db.collection("users")
                    .document(userId)
                    .collection("meters")
                    .document(meterId)
                    .delete()
                    .await()

                val readingsSnapshot = db.collection("readings")
                    .whereEqualTo("meterId", meterId)
                    .get()
                    .await()

                for (reading in readingsSnapshot.documents) {
                    reading.reference.delete().await()
                }

                Log.d(tag, "Meter deleted: $meterId (${readingsSnapshot.size()} readings)")
                _deleteMeterResult.value = DeleteMeterResult.Success

            } catch (e: Exception) {
                Log.e(tag, "Error deleting meter", e)
                _deleteMeterResult.value = DeleteMeterResult.Error(e.message ?: "Neznámá chyba")
            }
        }
    }

    fun resetDeleteResult() {
        _deleteResult.value = DeleteResult.Idle
    }

    fun resetDeleteMeterResult() {
        _deleteMeterResult.value = DeleteMeterResult.Idle
    }

    sealed class DeleteResult {
        object Idle : DeleteResult()
        object Success : DeleteResult()
        data class Error(val message: String) : DeleteResult()
    }

    sealed class DeleteMeterResult {
        object Idle : DeleteMeterResult()
        object Success : DeleteMeterResult()
        data class Error(val message: String) : DeleteMeterResult()
    }
}