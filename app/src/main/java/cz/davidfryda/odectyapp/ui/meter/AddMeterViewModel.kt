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
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class AddMeterViewModel : ViewModel() {
    private val db = Firebase.firestore
    private val tag = "AddMeterViewModel"

    private val _locations = MutableLiveData<List<Location>?>()
    val locations: LiveData<List<Location>?> = _locations

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _saveResult = MutableLiveData<SaveResult>()
    val saveResult: LiveData<SaveResult> = _saveResult

    fun loadLocations() {
        val userId = Firebase.auth.currentUser?.uid
        Log.d(tag, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        Log.d(tag, "loadLocations() CALLED")
        Log.d(tag, "userId = $userId")

        if (userId == null) {
            Log.e(tag, "❌ User not logged in")
            _locations.value = null
            return
        }

        _isLoading.value = true
        Log.d(tag, "📥 Starting Firestore query...")

        db.collection("users").document(userId).collection("locations")
            .orderBy("isDefault", Query.Direction.DESCENDING)
            .orderBy("createdAt", Query.Direction.ASCENDING)
            .get()
            .addOnSuccessListener { documents ->
                Log.d(tag, "✅ Firestore SUCCESS - ${documents.size()} documents")
                val locationsList = documents.mapNotNull { doc ->
                    val location = doc.toObject(Location::class.java).copy(id = doc.id)
                    Log.d(tag, "  ➕ Location: ${location.name} (${location.id})")
                    location
                }
                _locations.value = locationsList
                _isLoading.value = false
                Log.d(tag, "✅ Published ${locationsList.size} locations to LiveData")
                Log.d(tag, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
            }
            .addOnFailureListener { e ->
                Log.e(tag, "❌ Firestore FAILURE", e)
                _locations.value = null
                _isLoading.value = false
                Log.d(tag, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
            }
    }

    fun saveMeter(
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
                val meterRef = db.collection("users")
                    .document(userId)
                    .collection("meters")
                    .document()

                val meterData = hashMapOf(
                    "userId" to userId,
                    "locationId" to locationId,
                    "name" to name.trim(),
                    "type" to type.trim()
                )

                meterRef.set(meterData).await()

                Log.d(tag, "Meter saved successfully: ${meterRef.id}")
                _saveResult.value = SaveResult.Success
                _isLoading.value = false

            } catch (e: Exception) {
                Log.e(tag, "Error saving meter", e)
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