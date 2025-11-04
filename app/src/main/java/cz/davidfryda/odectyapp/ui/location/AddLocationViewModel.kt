package cz.davidfryda.odectyapp.ui.location

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Timestamp
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.Date

class AddLocationViewModel : ViewModel() {
    private val db = Firebase.firestore
    private val tag = "AddLocationViewModel"

    private val _saveResult = MutableLiveData<SaveResult>()
    val saveResult: LiveData<SaveResult> = _saveResult

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    fun saveLocation(
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

        val userId = Firebase.auth.currentUser?.uid
        if (userId == null) {
            _saveResult.value = SaveResult.Error("Uživatel není přihlášen")
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
                        doc.reference.update("isDefault", false).await()
                    }
                }

                // Vytvoř novou lokaci
                val locationRef = db.collection("users")
                    .document(userId)
                    .collection("locations")
                    .document()

                val locationData = hashMapOf(
                    "name" to name.trim(),
                    "address" to address.trim(),
                    "note" to note.trim(),
                    "isDefault" to setAsDefault,
                    "createdAt" to Timestamp(Date()),
                    "userId" to userId
                )

                locationRef.set(locationData).await()

                // Pokud je to výchozí, aktualizuj user dokument
                if (setAsDefault) {
                    db.collection("users")
                        .document(userId)
                        .update("defaultLocationId", locationRef.id)
                        .await()
                }

                Log.d(tag, "Location saved successfully: ${locationRef.id}")
                _saveResult.value = SaveResult.Success
                _isLoading.value = false

            } catch (e: Exception) {
                Log.e(tag, "Error saving location", e)
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