package cz.davidfryda.odectyapp.ui.location

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.ktx.auth // Ponecháno pro fallback
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import cz.davidfryda.odectyapp.data.Location
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class LocationListViewModel : ViewModel() {
    private val db = Firebase.firestore
    private val tag = "LocationListViewModel"
    private val auth = Firebase.auth // Instance pro fallback

    private val _locations = MutableLiveData<List<Location>>()
    val locations: LiveData<List<Location>> = _locations

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _deleteResult = MutableLiveData<DeleteResult>()
    val deleteResult: LiveData<DeleteResult> = _deleteResult

    private val _setDefaultResult = MutableLiveData<SetDefaultResult>()
    val setDefaultResult: LiveData<SetDefaultResult> = _setDefaultResult

    // NOVÉ: Proměnná pro uložení ID uživatele, se kterým pracujeme
    private var targetUserId: String? = null

    // PŮVODNÍ FUNKCE PŘEPRACOVÁNA
    /**
     * Načte lokace buď pro konkrétního uživatele (pokud je předáno userId - režim Master)
     * nebo pro aktuálně přihlášeného uživatele (pokud je userId null - běžný režim).
     */
    fun loadLocationsForUser(userId: String?) {
        // Klíčová logika: Urči, pro koho se bude načítat
        val idToQuery = userId ?: auth.currentUser?.uid

        if (idToQuery == null) {
            Log.e(tag, "Chyba: Uživatel není identifikován (ani z argumentu, ani přihlášen).")
            _locations.value = emptyList()
            return
        }

        // Ulož si ID pro pozdější použití (mazání, nastavení výchozí)
        this.targetUserId = idToQuery
        Log.d(tag, "Načítám lokace pro uživatele: $idToQuery")

        _isLoading.value = true

        db.collection("users").document(idToQuery).collection("locations")
            .orderBy("isDefault", Query.Direction.DESCENDING)
            .orderBy("createdAt", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshots, error ->
                _isLoading.value = false

                if (error != null) {
                    Log.e(tag, "Error loading locations for $idToQuery", error)
                    _locations.value = emptyList()
                    return@addSnapshotListener
                }

                val locationsList = snapshots?.documents?.mapNotNull { doc ->
                    doc.toObject(Location::class.java)?.copy(id = doc.id)
                } ?: emptyList()

                // Pro každou lokaci spočítej počet měřáků (použij správné ID!)
                loadMeterCounts(idToQuery, locationsList)
            }
    }

    // UPRAVENO: Používá předané `userId`
    private fun loadMeterCounts(userId: String, locationsList: List<Location>) {
        viewModelScope.launch {
            val locationsWithCounts = locationsList.map { location ->
                try {
                    val meterCount = db.collection("users")
                        .document(userId) // POUŽIJ SPRÁVNÉ ID
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

    // UPRAVENO: Používá `targetUserId`
    fun deleteLocation(locationId: String) {
        // val userId = Firebase.auth.currentUser?.uid // TOTO JE ŠPATNĚ
        if (targetUserId == null) {
            _deleteResult.value = DeleteResult.Error("User not logged in")
            return
        }

        viewModelScope.launch {
            _isLoading.value = true
            try {
                // Zkontroluj, jestli má lokace nějaké měřáky
                val metersSnapshot = db.collection("users")
                    .document(targetUserId!!) // POUŽIJ ULOŽENÉ ID
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
                    .document(targetUserId!!) // POUŽIJ ULOŽENÉ ID
                    .collection("locations")
                    .document(locationId)
                    .delete()
                    .await()

                Log.d(tag, "Location $locationId deleted successfully for user $targetUserId")
                _deleteResult.value = DeleteResult.Success
                _isLoading.value = false

            } catch (e: Exception) {
                Log.e(tag, "Error deleting location", e)
                _deleteResult.value = DeleteResult.Error(e.message ?: "Unknown error")
                _isLoading.value = false
            }
        }
    }

    // UPRAVENO: Používá `targetUserId`
    fun setAsDefault(locationId: String) {
        // val userId = Firebase.auth.currentUser?.uid // TOTO JE ŠPATNĚ
        if (targetUserId == null) {
            _setDefaultResult.value = SetDefaultResult.Error("User not logged in")
            return
        }

        viewModelScope.launch {
            _isLoading.value = true
            try {
                // Najdi aktuální výchozí lokaci
                val currentDefaultSnapshot = db.collection("users")
                    .document(targetUserId!!) // POUŽIJ ULOŽENÉ ID
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
                    .document(targetUserId!!) // POUŽIJ ULOŽENÉ ID
                    .collection("locations")
                    .document(locationId)
                    .update("isDefault", true)
                    .await()

                // Aktualizuj defaultLocationId v user dokumentu
                db.collection("users")
                    .document(targetUserId!!) // POUŽIJ ULOŽENÉ ID
                    .update("defaultLocationId", locationId)
                    .await()

                Log.d(tag, "Location $locationId set as default for user $targetUserId")
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
