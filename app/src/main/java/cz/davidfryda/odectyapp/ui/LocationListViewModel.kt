package cz.davidfryda.odectyapp.ui.location

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

class LocationListViewModel : ViewModel() {
    private val db = Firebase.firestore
    private val tag = "LocationListViewModel"

    private val _locations = MutableLiveData<List<Location>>()
    val locations: LiveData<List<Location>> = _locations

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _deleteResult = MutableLiveData<DeleteResult>()
    val deleteResult: LiveData<DeleteResult> = _deleteResult

    private val _setDefaultResult = MutableLiveData<SetDefaultResult>()
    val setDefaultResult: LiveData<SetDefaultResult> = _setDefaultResult

    fun loadLocations() {
        val userId = Firebase.auth.currentUser?.uid
        if (userId == null) {
            Log.e(tag, "User not logged in")
            _locations.value = emptyList()
            return
        }

        _isLoading.value = true

        db.collection("users").document(userId).collection("locations")
            .orderBy("isDefault", Query.Direction.DESCENDING)
            .orderBy("createdAt", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshots, error ->
                _isLoading.value = false

                if (error != null) {
                    Log.e(tag, "Error loading locations", error)
                    _locations.value = emptyList()
                    return@addSnapshotListener
                }

                val locationsList = snapshots?.documents?.mapNotNull { doc ->
                    doc.toObject(Location::class.java)?.copy(id = doc.id)
                } ?: emptyList()

                // Pro každou lokaci spočítej počet měřáků
                loadMeterCounts(userId, locationsList)
            }
    }

    private fun loadMeterCounts(userId: String, locationsList: List<Location>) {
        viewModelScope.launch {
            val locationsWithCounts = locationsList.map { location ->
                try {
                    val meterCount = db.collection("users")
                        .document(userId)
                        .collection("meters")
                        .whereEqualTo("locationId", location.id)
                        .get()
                        .await()
                        .size()

                    location.copy(meterCount = meterCount)
                } catch (e: Exception) {
                    Log.e(tag, "Error loading meter count for location ${location.id}", e)
                    location
                }
            }

            _locations.value = locationsWithCounts
        }
    }

    fun deleteLocation(locationId: String) {
        val userId = Firebase.auth.currentUser?.uid
        if (userId == null) {
            _deleteResult.value = DeleteResult.Error("User not logged in")
            return
        }

        viewModelScope.launch {
            _isLoading.value = true
            try {
                // Zkontroluj, jestli má lokace nějaké měřáky
                val metersSnapshot = db.collection("users")
                    .document(userId)
                    .collection("meters")
                    .whereEqualTo("locationId", locationId)
                    .get()
                    .await()

                if (metersSnapshot.size() > 0) {
                    _deleteResult.value = DeleteResult.Error(
                        "Nelze smazat místo s měřáky. Nejdřív přesuňte nebo smažte všechny měřáky."
                    )
                    _isLoading.value = false
                    return@launch
                }

                // Smaž lokaci
                db.collection("users")
                    .document(userId)
                    .collection("locations")
                    .document(locationId)
                    .delete()
                    .await()

                Log.d(tag, "Location $locationId deleted successfully")
                _deleteResult.value = DeleteResult.Success
                _isLoading.value = false

            } catch (e: Exception) {
                Log.e(tag, "Error deleting location", e)
                _deleteResult.value = DeleteResult.Error(e.message ?: "Unknown error")
                _isLoading.value = false
            }
        }
    }

    fun setAsDefault(locationId: String) {
        val userId = Firebase.auth.currentUser?.uid
        if (userId == null) {
            _setDefaultResult.value = SetDefaultResult.Error("User not logged in")
            return
        }

        viewModelScope.launch {
            _isLoading.value = true
            try {
                // Najdi aktuální výchozí lokaci
                val currentDefaultSnapshot = db.collection("users")
                    .document(userId)
                    .collection("locations")
                    .whereEqualTo("isDefault", true)
                    .get()
                    .await()

                // Odeber výchozí status z aktuální výchozí lokace
                for (doc in currentDefaultSnapshot.documents) {
                    doc.reference.update("isDefault", false).await()
                }

                // Nastav novou výchozí lokaci
                db.collection("users")
                    .document(userId)
                    .collection("locations")
                    .document(locationId)
                    .update("isDefault", true)
                    .await()

                // Aktualizuj defaultLocationId v user dokumentu
                db.collection("users")
                    .document(userId)
                    .update("defaultLocationId", locationId)
                    .await()

                Log.d(tag, "Location $locationId set as default")
                _setDefaultResult.value = SetDefaultResult.Success
                _isLoading.value = false

            } catch (e: Exception) {
                Log.e(tag, "Error setting default location", e)
                _setDefaultResult.value = SetDefaultResult.Error(e.message ?: "Unknown error")
                _isLoading.value = false
            }
        }
    }

    fun resetDeleteResult() {
        _deleteResult.value = DeleteResult.Idle
    }

    fun resetSetDefaultResult() {
        _setDefaultResult.value = SetDefaultResult.Idle
    }

    sealed class DeleteResult {
        object Idle : DeleteResult()
        object Success : DeleteResult()
        data class Error(val message: String) : DeleteResult()
    }

    sealed class SetDefaultResult {
        object Idle : SetDefaultResult()
        object Success : SetDefaultResult()
        data class Error(val message: String) : SetDefaultResult()
    }
}