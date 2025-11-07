package cz.davidfryda.odectyapp.ui.location

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import cz.davidfryda.odectyapp.data.Location
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class EditLocationViewModel : ViewModel() {
    private val db = Firebase.firestore
    private val tag = "EditLocationViewModel"

    private val _location = MutableLiveData<Location?>()
    val location: LiveData<Location?> = _location

    private val _meterCount = MutableLiveData<Int>()
    val meterCount: LiveData<Int> = _meterCount

    private val _saveResult = MutableLiveData<SaveResult>()
    val saveResult: LiveData<SaveResult> = _saveResult

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    // ✨ OPRAVA: Sem si uložíme ID, se kterými budeme pracovat
    private var targetUserId: String? = null
    private var currentLocationId: String? = null

    // ✨ OPRAVA: Funkce nyní přijímá 'userId' (který může být null)
    fun loadLocation(userId: String?, locationId: String) {
        // Pokud 'userId' přišel (master upravuje cizí), použijeme ten.
        // Pokud je 'userId' null (uživatel upravuje své), vezmeme ID přihlášeného.
        val uidToUse = userId ?: Firebase.auth.currentUser?.uid

        if (uidToUse == null) {
            Log.e(tag, "User not logged in and no userId provided")
            _location.value = null
            return
        }

        // Uložíme si ID pro pozdější použití (v 'updateLocation')
        this.targetUserId = uidToUse
        this.currentLocationId = locationId
        _isLoading.value = true

        viewModelScope.launch {
            try {
                // ✨ OPRAVA: Používáme 'uidToUse'
                val doc = db.collection("users")
                    .document(uidToUse)
                    .collection("locations")
                    .document(locationId)
                    .get()
                    .await()

                if (doc.exists()) {
                    val locationData = doc.toObject(Location::class.java)?.copy(id = doc.id)
                    _location.value = locationData
                    // Načteme počet měřáků pro správného uživatele
                    loadMeterCount(uidToUse, locationId)
                } else {
                    Log.e(tag, "Location not found: $locationId for user $uidToUse")
                    _location.value = null
                }
                _isLoading.value = false

            } catch (e: Exception) {
                Log.e(tag, "Error loading location $locationId for user $uidToUse", e)
                _location.value = null
                _isLoading.value = false
            }
        }
    }

    private suspend fun loadMeterCount(userId: String, locationId: String) {
        try {
            val count = db.collection("users")
                .document(userId)
                .collection("meters")
                .whereEqualTo("locationId", locationId)
                .get()
                .await()
                .size()
            _meterCount.value = count
        } catch (e: Exception) {
            Log.e(tag, "Error loading meter count", e)
            _meterCount.value = 0
        }
    }

    // ✨ OPRAVA: Funkce už nepotřebuje ID jako argumenty, pamatuje si je
    fun updateLocation(
        name: String,
        address: String,
        note: String,
        setAsDefault: Boolean
    ) {
        // Validace
        if (name.isBlank()) {
            _saveResult.value = SaveResult.Error("Název místa je povinný")
            return
        }
        if (address.isBlank()) {
            _saveResult.value = SaveResult.Error("Adresa je povinná")
            return
        }

        // ✨ OPRAVA: Použijeme uložená ID
        val userId = targetUserId
        val locationId = currentLocationId

        if (userId == null || locationId == null) {
            _saveResult.value = SaveResult.Error("Chyba: Uživatel nebo lokace není specifikována.")
            return
        }

        viewModelScope.launch {
            _isLoading.value = true
            try {
                // Pokud má být toto místo výchozí, odeber výchozí status z ostatních
                if (setAsDefault) {
                    val currentDefaultSnapshot = db.collection("users")
                        .document(userId)
                        .collection("locations")
                        .whereEqualTo("isDefault", true)
                        .get()
                        .await()

                    for (doc in currentDefaultSnapshot.documents) {
                        if (doc.id != locationId) {
                            doc.reference.update("isDefault", false).await()
                        }
                    }
                }

                // Aktualizuj lokaci
                val updates = hashMapOf<String, Any>(
                    "name" to name.trim(),
                    "address" to address.trim(),
                    "note" to note.trim(),
                    "isDefault" to setAsDefault
                )

                db.collection("users")
                    .document(userId)
                    .collection("locations")
                    .document(locationId)
                    .update(updates)
                    .await()

                // Pokud je to výchozí, aktualizuj i hlavní dokument uživatele
                if (setAsDefault) {
                    db.collection("users")
                        .document(userId)
                        .update("defaultLocationId", locationId)
                        .await()
                }

                Log.d(tag, "Location updated successfully: $locationId for user $userId")
                _saveResult.value = SaveResult.Success
                _isLoading.value = false

            } catch (e: Exception) {
                Log.e(tag, "Error updating location for user $userId", e)
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