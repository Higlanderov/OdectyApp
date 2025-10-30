package cz.davidfryda.odectyapp.ui.master

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import cz.davidfryda.odectyapp.data.Meter
import cz.davidfryda.odectyapp.data.UserData
import cz.davidfryda.odectyapp.ui.user.SaveResult
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class MasterUserDetailViewModel : ViewModel() {
    private val db = Firebase.firestore

    private val _meters = MutableLiveData<List<Meter>>()
    val meters: LiveData<List<Meter>> = _meters

    // ✨ NOVÉ: LiveData pro jméno uživatele
    private val _userName = MutableLiveData<String>()
    val userName: LiveData<String> = _userName

    private val _saveDescriptionResult = MutableLiveData<SaveResult>(SaveResult.Idle)
    val saveDescriptionResult: LiveData<SaveResult> = _saveDescriptionResult

    private val tag = "MasterUserDetailVM"

    fun fetchMetersForUser(userId: String) {
        // ✨ NOVÉ: Načteme jméno uživatele
        db.collection("users").document(userId).get()
            .addOnSuccessListener { document ->
                val user = document.toObject(UserData::class.java)
                if (user != null) {
                    _userName.value = "${user.surname} ${user.name}".trim()
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
}