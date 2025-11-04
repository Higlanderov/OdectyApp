package cz.davidfryda.odectyapp.ui.meter

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import cz.davidfryda.odectyapp.data.Location
import cz.davidfryda.odectyapp.data.Meter
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class EditMeterViewModel : ViewModel() {
    private val db = Firebase.firestore
    private val tag = "EditMeterViewModel"

    private val _meter = MutableLiveData<Meter?>()
    val meter: LiveData<Meter?> = _meter

    private val _locations = MutableLiveData<List<Location>?>()
    val locations: LiveData<List<Location>?> = _locations

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _saveResult = MutableLiveData<SaveResult>()
    val saveResult: LiveData<SaveResult> = _saveResult

    fun loadMeter(meterId: String) {
        val userId = Firebase.auth.currentUser?.uid
        if (userId == null) {
            Log.e(tag, "User not logged in")
            _meter.value = null
            return
        }

        _isLoading.value = true

        db.collection("users")
            .document(userId)
            .collection("meters")
            .document(meterId)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val meter = document.toObject(Meter::class.java)?.copy(id = document.id)
                    _meter.value = meter
                    Log.d(tag, "Meter loaded: ${meter?.name}")
                } else {
                    Log.e(tag, "Meter not found: $meterId")
                    _meter.value = null
                }
                _isLoading.value = false
            }
            .addOnFailureListener { e ->
                Log.e(tag, "Error loading meter", e)
                _meter.value = null
                _isLoading.value = false
            }
    }

    fun loadLocations() {
        val userId = Firebase.auth.currentUser?.uid
        if (userId == null) {
            Log.e(tag, "User not logged in")
            _locations.value = null
            return
        }

        db.collection("users").document(userId).collection("locations")
            .get()
            .addOnSuccessListener { documents ->
                val locationsList = documents.mapNotNull { doc ->
                    doc.toObject(Location::class.java).copy(id = doc.id)
                }

                // Seřaď v kódu: výchozí první, pak podle data vytvoření
                val sortedLocations = locationsList.sortedWith(
                    compareByDescending<Location> { it.isDefault }
                        .thenBy { it.createdAt }
                )

                _locations.value = sortedLocations
                Log.d(tag, "Loaded ${sortedLocations.size} locations")
            }
            .addOnFailureListener { e ->
                Log.e(tag, "Error loading locations", e)
                _locations.value = null
            }
    }

    fun updateMeter(
        meterId: String,
        locationId: String,
        name: String,
        type: String
    ) {
        // Validace
        if (locationId.isBlank()) {
            _saveResult.value = SaveResult.Error("Odběrné místo je povinné")
            return
        }

        if (name.isBlank()) {
            _saveResult.value = SaveResult.Error("Název měřáku je povinný")
            return
        }

        if (type.isBlank()) {
            _saveResult.value = SaveResult.Error("Typ měřáku je povinný")
            return
        }

        val userId = Firebase.auth.currentUser?.uid
        if (userId == null) {
            _saveResult.value = SaveResult.Error("Uživatel není přihlášen")
            return
        }

        viewModelScope.launch {
            _isLoading.value = true
            try {
                val updates = hashMapOf<String, Any>(
                    "locationId" to locationId,
                    "name" to name.trim(),
                    "type" to type.trim()
                )

                db.collection("users")
                    .document(userId)
                    .collection("meters")
                    .document(meterId)
                    .update(updates)
                    .await()

                Log.d(tag, "Meter updated successfully: $meterId")
                _saveResult.value = SaveResult.Success
                _isLoading.value = false

            } catch (e: Exception) {
                Log.e(tag, "Error updating meter", e)
                _saveResult.value = SaveResult.Error(e.message ?: "Neznámá chyba")
                _isLoading.value = false
            }
        }
    }

    fun resetSaveResult() {
        _saveResult.value = SaveResult.Idle
    }

    sealed class SaveResult {
        object Idle : SaveResult()
        object Success : SaveResult()
        data class Error(val message: String) : SaveResult()
    }
}