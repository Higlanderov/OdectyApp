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
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class MasterLocationListViewModel : ViewModel() {
    private val db = Firebase.firestore
    private val tag = "MasterLocationListVM" // Změněn tag

    private val _locations = MutableLiveData<List<Location>>()
    val locations: LiveData<List<Location>> = _locations

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _deleteResult = MutableLiveData<LocationListViewModel.DeleteResult>()
    val deleteResult: LiveData<LocationListViewModel.DeleteResult> = _deleteResult

    private val _setDefaultResult = MutableLiveData<LocationListViewModel.SetDefaultResult>()
    val setDefaultResult: LiveData<LocationListViewModel.SetDefaultResult> = _setDefaultResult

    // ID uživatele, pro kterého pracujeme
    private var targetUserId: String? = null

    /**
     * Načte lokace pro konkrétního uživatele (režim Master).
     */
    fun loadLocationsForUser(userId: String) {
        // Zde už není fallback, userId je povinné
        this.targetUserId = userId
        Log.d(tag, "Načítám lokace pro uživatele: $userId")

        _isLoading.value = true

        db.collection("users").document(userId).collection("locations")
            .orderBy("isDefault", Query.Direction.DESCENDING)
            .orderBy("createdAt", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshots, error ->
                _isLoading.value = false

                if (error != null) {
                    Log.e(tag, "Error loading locations for $userId", error)
                    _locations.value = emptyList()
                    return@addSnapshotListener
                }

                val locationsList = snapshots?.documents?.mapNotNull { doc ->
                    doc.toObject(Location::class.java)?.copy(id = doc.id)
                } ?: emptyList()

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

    // Funkce pro mazání a nastavení výchozí jsou stejné,
    // jen musíme zajistit, že používají `targetUserId`

    fun deleteLocation(locationId: String) {
        if (targetUserId == null) {
            _deleteResult.value = LocationListViewModel.DeleteResult.Error("User ID není nastaveno")
            return
        }

        viewModelScope.launch {
            _isLoading.value = true
            try {
                val metersSnapshot = db.collection("users")
                    .document(targetUserId!!)
                    .collection("meters")
                    .whereEqualTo("locationId", locationId)
                    .get()
                    .await()

                if (metersSnapshot.size() > 0) {
                    _deleteResult.value = LocationListViewModel.DeleteResult.Error(
                        "Nelze smazat místo s měřáky."
                    )
                    _isLoading.value = false
                    return@launch
                }

                db.collection("users")
                    .document(targetUserId!!)
                    .collection("locations")
                    .document(locationId)
                    .delete()
                    .await()

                Log.d(tag, "Location $locationId deleted for user $targetUserId")
                _deleteResult.value = LocationListViewModel.DeleteResult.Success
                _isLoading.value = false

            } catch (e: Exception) {
                Log.e(tag, "Error deleting location", e)
                _deleteResult.value = LocationListViewModel.DeleteResult.Error(e.message ?: "Unknown error")
                _isLoading.value = false
            }
        }
    }

    fun setAsDefault(locationId: String) {
        if (targetUserId == null) {
            _setDefaultResult.value = LocationListViewModel.SetDefaultResult.Error("User ID není nastaveno")
            return
        }

        viewModelScope.launch {
            _isLoading.value = true
            try {
                val currentDefaultSnapshot = db.collection("users")
                    .document(targetUserId!!)
                    .collection("locations")
                    .whereEqualTo("isDefault", true)
                    .get()
                    .await()

                for (doc in currentDefaultSnapshot.documents) {
                    doc.reference.update("isDefault", false).await()
                }

                db.collection("users")
                    .document(targetUserId!!)
                    .collection("locations")
                    .document(locationId)
                    .update("isDefault", true)
                    .await()

                db.collection("users")
                    .document(targetUserId!!)
                    .update("defaultLocationId", locationId)
                    .await()

                Log.d(tag, "Location $locationId set as default for user $targetUserId")
                _setDefaultResult.value = LocationListViewModel.SetDefaultResult.Success
                _isLoading.value = false

            } catch (e: Exception) {
                Log.e(tag, "Error setting default location", e)
                _setDefaultResult.value = LocationListViewModel.SetDefaultResult.Error(e.message ?: "Unknown error")
                _isLoading.value = false
            }
        }
    }

    // Resetovací funkce mohou zůstat stejné
    fun resetDeleteResult() { _deleteResult.value = LocationListViewModel.DeleteResult.Idle }
    fun resetSetDefaultResult() { _setDefaultResult.value = LocationListViewModel.SetDefaultResult.Idle }

    // Používáme stejné sealed classes jako LocationListViewModel
    // (Nebo si můžete vytvořit vlastní, ale není to nutné)
}