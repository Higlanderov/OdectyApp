package cz.davidfryda.odectyapp.ui.master

import android.util.Log // Import pro logování
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope // Import pro viewModelScope
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import cz.davidfryda.odectyapp.data.Meter
import cz.davidfryda.odectyapp.ui.user.SaveResult // Import pro SaveResult
import kotlinx.coroutines.launch // Import pro launch
import kotlinx.coroutines.tasks.await // Import pro await

class MasterUserDetailViewModel : ViewModel() {
    private val db = Firebase.firestore

    private val _meters = MutableLiveData<List<Meter>>()
    val meters: LiveData<List<Meter>> = _meters

    // --- ZAČÁTEK NOVÉ ČÁSTI ---
    // LiveData pro výsledek uložení popisu
    private val _saveDescriptionResult = MutableLiveData<SaveResult>(SaveResult.Idle) // Výchozí stav Idle
    val saveDescriptionResult: LiveData<SaveResult> = _saveDescriptionResult
    // --- KONEC NOVÉ ČÁSTI ---

    private val TAG = "MasterUserDetailVM" // Tag pro logování

    fun fetchMetersForUser(userId: String) {
        // Používáme addSnapshotListener pro real-time aktualizace
        db.collection("users").document(userId).collection("meters")
            .addSnapshotListener { snapshots, error ->
                if (error != null) {
                    Log.e(TAG, "fetchMetersForUser: Chyba při načítání měřáků pro $userId", error)
                    // Můžeme nastavit chybový stav nebo prázdný seznam
                    _meters.value = emptyList()
                    return@addSnapshotListener
                }
                if (snapshots != null) {
                    // Mapujeme dokumenty na Meter objekty, včetně nového pole masterDescription
                    _meters.value = snapshots.map { doc ->
                        doc.toObject(Meter::class.java).copy(id = doc.id)
                    }.sortedBy { it.name } // Seřadíme podle jména
                    Log.d(TAG, "fetchMetersForUser: Načteno ${_meters.value?.size ?: 0} měřáků pro $userId.")
                } else {
                    _meters.value = emptyList()
                    Log.d(TAG, "fetchMetersForUser: Snapshot je null pro $userId.")
                }
            }
    }

    // --- ZAČÁTEK NOVÉ METODY ---
    // Metoda pro uložení popisu přidaného masterem
    fun saveMasterDescription(userId: String, meterId: String, description: String) {
        viewModelScope.launch {
            _saveDescriptionResult.value = SaveResult.Loading
            try {
                // Použijeme update pro změnu pouze jednoho pole
                // Pokud description je prázdný string, uložíme null (efektivně smazání popisu)
                val descriptionToSave = description.ifBlank { null }
                db.collection("users").document(userId)
                    .collection("meters").document(meterId)
                    .update("masterDescription", descriptionToSave)
                    .await()
                _saveDescriptionResult.value = SaveResult.Success
                Log.d(TAG, "saveMasterDescription: Popis pro měřák $meterId uživatele $userId uložen jako: '$descriptionToSave'.")

            } catch (e: Exception) {
                _saveDescriptionResult.value = SaveResult.Error(e.message ?: "Chyba při ukládání popisu.")
                Log.e(TAG, "saveMasterDescription: Chyba při ukládání popisu pro $meterId", e)
            }
        }
    }

    // Metoda pro resetování stavu výsledku (volá se z Fragmentu)
    fun resetSaveDescriptionResult() {
        _saveDescriptionResult.value = SaveResult.Idle
        Log.d(TAG, "resetSaveDescriptionResult: Stav resetován na Idle.")
    }
    // --- KONEC NOVÉ METODY ---
}