package cz.davidfryda.odectyapp.ui.master

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.ktx.storage
import cz.davidfryda.odectyapp.data.Meter
import cz.davidfryda.odectyapp.data.UserData
import cz.davidfryda.odectyapp.ui.user.SaveResult
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

@Suppress("unused")
class MasterUserDetailViewModel : ViewModel() {
    private val db = Firebase.firestore
    private val storage = Firebase.storage

    private val _meters = MutableLiveData<List<Meter>>()
    val meters: LiveData<List<Meter>> = _meters

    private val _userName = MutableLiveData<String>()
    val userName: LiveData<String> = _userName

    private val _saveDescriptionResult = MutableLiveData<SaveResult>(SaveResult.Idle)
    val saveDescriptionResult: LiveData<SaveResult> = _saveDescriptionResult

    // ✨ NOVÉ: LiveData pro výsledek přidání měřáku
    private val _addResult = MutableLiveData<SaveResult>(SaveResult.Idle)
    val addResult: LiveData<SaveResult> = _addResult

    // ✨ NOVÉ: LiveData pro výsledek úpravy měřáku
    private val _updateResult = MutableLiveData<SaveResult>(SaveResult.Idle)
    val updateResult: LiveData<SaveResult> = _updateResult

    // ✨ NOVÉ: LiveData pro výsledek smazání měřáku
    private val _deleteResult = MutableLiveData<SaveResult>(SaveResult.Idle)
    val deleteResult: LiveData<SaveResult> = _deleteResult

    private val tag = "MasterUserDetailVM"

    fun fetchMetersForUser(userId: String) {
        // Načteme jméno uživatele
        db.collection("users").document(userId).get()
            .addOnSuccessListener { document ->
                val user = document.toObject(UserData::class.java)
                if (user != null) {
                    _userName.value = "${user.name} ${user.surname}".trim()
                    Log.d(tag, "Načteno jméno uživatele: ${_userName.value}")
                } else {
                    Log.w(tag, "UserData pro $userId je null")
                }
            }
            .addOnFailureListener { e ->
                Log.e(tag, "Chyba při načítání jména uživatele $userId", e)
            }

        // Načtení měřáků
        db.collection("users").document(userId).collection("meters")
            .addSnapshotListener { snapshots, error ->
                if (error != null) {
                    Log.e(tag, "fetchMetersForUser: Chyba při načítání měřáků pro $userId", error)
                    _meters.value = emptyList()
                    return@addSnapshotListener
                }
                if (snapshots != null) {
                    _meters.value = snapshots.map { doc ->
                        doc.toObject(Meter::class.java).copy(id = doc.id)
                    }.sortedBy { it.name }
                    Log.d(tag, "fetchMetersForUser: Načteno ${_meters.value?.size ?: 0} měřáků pro $userId.")
                } else {
                    _meters.value = emptyList()
                    Log.d(tag, "fetchMetersForUser: Snapshot je null pro $userId.")
                }
            }
    }

    fun saveMasterDescription(userId: String, meterId: String, description: String) {
        viewModelScope.launch {
            _saveDescriptionResult.value = SaveResult.Loading
            try {
                val descriptionToSave = description.ifBlank { null }
                db.collection("users").document(userId)
                    .collection("meters").document(meterId)
                    .update("masterDescription", descriptionToSave)
                    .await()
                _saveDescriptionResult.value = SaveResult.Success
                Log.d(tag, "saveMasterDescription: Popis pro měřák $meterId uživatele $userId uložen jako: '$descriptionToSave'.")

            } catch (e: Exception) {
                _saveDescriptionResult.value = SaveResult.Error(e.message ?: "Chyba při ukládání popisu.")
                Log.e(tag, "saveMasterDescription: Chyba při ukládání popisu pro $meterId", e)
            }
        }
    }

    fun resetSaveDescriptionResult() {
        _saveDescriptionResult.value = SaveResult.Idle
        Log.d(tag, "resetSaveDescriptionResult: Stav resetován na Idle.")
    }

    // ✨ NOVÁ METODA: Přidat měřák
    fun addMeter(name: String, type: String, userId: String) {
        Log.d(tag, "addMeter: name='$name', type='$type', userId='$userId'")
        _addResult.value = SaveResult.Loading
        viewModelScope.launch {
            try {
                val meterData = hashMapOf(
                    "name" to name,
                    "type" to type,
                    "createdAt" to com.google.firebase.Timestamp.now()
                )

                db.collection("users")
                    .document(userId)
                    .collection("meters")
                    .add(meterData)
                    .await()

                Log.d(tag, "Meter added successfully")
                _addResult.value = SaveResult.Success
            } catch (e: Exception) {
                Log.e(tag, "Error adding meter", e)
                _addResult.value = SaveResult.Error(e.message ?: "Neznámá chyba")
            }
        }
    }

    fun resetAddResult() {
        _addResult.value = SaveResult.Idle
        Log.d(tag, "resetAddResult: Stav resetován na Idle.")
    }

    // ✨ NOVÁ METODA: Upravit název měřáku
    fun updateMeterName(meterId: String, newName: String, userId: String) {
        Log.d(tag, "updateMeterName: meterId='$meterId', newName='$newName', userId='$userId'")
        _updateResult.value = SaveResult.Loading
        viewModelScope.launch {
            try {
                db.collection("users")
                    .document(userId)
                    .collection("meters")
                    .document(meterId)
                    .update("name", newName)
                    .await()

                Log.d(tag, "Meter name updated successfully")
                _updateResult.value = SaveResult.Success
            } catch (e: Exception) {
                Log.e(tag, "Error updating meter name", e)
                _updateResult.value = SaveResult.Error(e.message ?: "Neznámá chyba")
            }
        }
    }

    fun resetUpdateResult() {
        _updateResult.value = SaveResult.Idle
        Log.d(tag, "resetUpdateResult: Stav resetován na Idle.")
    }

    // ✨ NOVÁ METODA: Smazat měřák včetně odečtů a fotek
    fun deleteMeter(meterId: String, userId: String) {
        Log.d(tag, "deleteMeter: meterId='$meterId', userId='$userId'")
        _deleteResult.value = SaveResult.Loading
        viewModelScope.launch {
            try {
                // 1. Smazat všechny odečty a jejich fotky
                val readingsSnapshot = db.collection("users")
                    .document(userId)
                    .collection("meters")
                    .document(meterId)
                    .collection("readings")
                    .get()
                    .await()

                Log.d(tag, "Found ${readingsSnapshot.documents.size} readings to delete")

                // Smazat fotky z každého odečtu
                for (readingDoc in readingsSnapshot.documents) {
                    val photoUrl = readingDoc.getString("photoUrl")
                    if (!photoUrl.isNullOrEmpty()) {
                        try {
                            val photoRef = storage.getReferenceFromUrl(photoUrl)
                            photoRef.delete().await()
                            Log.d(tag, "Deleted photo: $photoUrl")
                        } catch (e: Exception) {
                            Log.w(tag, "Failed to delete photo: $photoUrl", e)
                        }
                    }
                    // Smazat odečet
                    readingDoc.reference.delete().await()
                }

                // 2. Smazat měřák
                db.collection("users")
                    .document(userId)
                    .collection("meters")
                    .document(meterId)
                    .delete()
                    .await()

                Log.d(tag, "Meter deleted successfully")
                _deleteResult.value = SaveResult.Success
            } catch (e: Exception) {
                Log.e(tag, "Error deleting meter", e)
                _deleteResult.value = SaveResult.Error(e.message ?: "Neznámá chyba")
            }
        }
    }

    fun resetDeleteResult() {
        _deleteResult.value = SaveResult.Idle
        Log.d(tag, "resetDeleteResult: Stav resetován na Idle.")
    }
}