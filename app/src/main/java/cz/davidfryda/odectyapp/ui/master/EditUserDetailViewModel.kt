package cz.davidfryda.odectyapp.ui.master

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.FirebaseFirestore
import cz.davidfryda.odectyapp.data.UserData
import cz.davidfryda.odectyapp.ui.user.SaveResult
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class EditUserDetailViewModel : ViewModel() {

    private val db = FirebaseFirestore.getInstance()
    private val tag = "EditUserDetailViewModel"

    private val _user = MutableLiveData<UserData?>()
    val user: LiveData<UserData?> = _user

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    // --- ✨ ZAČÁTEK OPRAVY (1/2) ---
    // Inicializujeme LiveData s výchozím stavem Idle
    private val _saveResult = MutableLiveData<SaveResult>(SaveResult.Idle)
    val saveResult: LiveData<SaveResult> = _saveResult
    // --- ✨ KONEC OPRAVY (1/2) ---

    fun loadUserData(userId: String) {
        _isLoading.value = true
        // Nastavíme stav na Idle při každém novém načítání, abychom resetovali UI
        _saveResult.value = SaveResult.Idle
        viewModelScope.launch {
            try {
                val userDocument = db.collection("users").document(userId).get().await()
                val userData = userDocument.toObject(UserData::class.java)
                _user.postValue(userData)
                Log.d(tag, "Načtena UserData pro editaci: $userData")
            } catch (e: Exception) {
                Log.e(tag, "Chyba při načítání dat uživatele pro editaci", e)
                _user.postValue(null)
            } finally {
                _isLoading.postValue(false)
            }
        }
    }

    fun saveUserData(
        userId: String,
        name: String,
        surname: String,
        address: String,
        phoneNumber: String,
        note: String
    ) {
        viewModelScope.launch {
            _saveResult.value = SaveResult.Loading

            val updates = mapOf(
                "name" to name,
                "surname" to surname,
                "address" to address,
                "phoneNumber" to phoneNumber,
                "note" to note
            )

            try {
                db.collection("users").document(userId)
                    .update(updates)
                    .await()
                _saveResult.value = SaveResult.Success
                Log.d(tag, "Uživatelská data pro $userId úspěšně aktualizována.")
            } catch (e: Exception) {
                Log.e(tag, "Chyba při ukládání dat pro $userId", e)
                _saveResult.value = SaveResult.Error(e.message ?: "Neznámá chyba při ukládání")
            }
        }
    }

    // --- ✨ ZAČÁTEK OPRAVY (2/2) ---
    // Volá se, když fragment opustí obrazovku
    fun doneNavigating() {
        // Místo `null` nastavíme stav `Idle`
        _saveResult.value = SaveResult.Idle
    }
    // --- ✨ KONEC OPRAVY (2/2) ---
}