package cz.davidfryda.odectyapp.ui.profile

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.SetOptions // <-- DŮLEŽITÝ IMPORT
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import cz.davidfryda.odectyapp.data.UserData
import cz.davidfryda.odectyapp.ui.user.SaveResult
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class ProfileViewModel : ViewModel() {
    private val db = Firebase.firestore
    private val currentUser = Firebase.auth.currentUser
    private val TAG = "ProfileViewModel"

    private val _userData = MutableLiveData<UserData?>()
    val userData: LiveData<UserData?> = _userData

    private val _saveResult = MutableLiveData<SaveResult>(SaveResult.Idle)
    val saveResult: LiveData<SaveResult> = _saveResult

    // Načtení dat (automaticky načte i nový e-mail)
    fun loadUserData() {
        val user = currentUser
        if (user == null) {
            Log.w(TAG, "loadUserData: Aktuální uživatel je null.")
            _userData.value = null
            return
        }
        val userId = user.uid
        Log.d(TAG, "loadUserData: Načítám data pro uživatele $userId")
        db.collection("users").document(userId).get()
            .addOnSuccessListener { document ->
                _userData.value = document.toObject(UserData::class.java)
                if (_userData.value != null) {
                    Log.d(TAG, "loadUserData: Data uživatele načtena úspěšně.")
                } else {
                    Log.w(TAG, "loadUserData: Dokument uživatele $userId nenalezen, je prázdný nebo se nepodařilo převést na UserData.")
                    _userData.value = null
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "loadUserData: Chyba při načítání dat uživatele.", e)
                _userData.value = null
            }
    }

    // Uložení nebo aktualizace dat
    fun saveOrUpdateUser(name: String, surname: String, address: String, phone: String) {
        viewModelScope.launch {
            _saveResult.value = SaveResult.Loading
            val user = currentUser
            if (user == null) {
                _saveResult.value = SaveResult.Error("Uživatel není přihlášen.")
                return@launch
            }
            val userId = user.uid

            // --- START ZMĚNY ---
            // Získáme e-mail z Auth a přidáme ho do mapy pro uložení
            val email = user.email ?: "" // Získáme e-mail přihlášeného uživatele

            val userMap = mapOf(
                "name" to name,
                "surname" to surname,
                "address" to address,
                "phoneNumber" to phone,
                "email" to email // <-- PŘIDÁNO ULOŽENÍ E-MAILU
            )
            // --- KONEC ZMĚNY ---

            try {
                // Použití .set() s merge zajistí, že se aktualizují POUZE pole v mapě
                db.collection("users").document(userId)
                    .set(userMap, SetOptions.merge()) // <-- Používáme MERGE
                    .await()

                _saveResult.value = SaveResult.Success
                Log.d(TAG, "saveOrUpdateUser: Data uživatele $userId úspěšně uložena.")

                // Aktualizujeme lokální data
                _userData.value = _userData.value?.copy(
                    name = name,
                    surname = surname,
                    address = address,
                    phoneNumber = phone,
                    email = email // <-- Aktualizace lokálního e-mailu
                ) ?: UserData(userId, name, surname, address, phone, email) // Fallback

            } catch (e: Exception) {
                _saveResult.value = SaveResult.Error(e.message ?: "Chyba při ukládání.")
                Log.e(TAG, "saveOrUpdateUser: Chyba při ukládání dat uživatele $userId.", e)
            }
        }
    }

    // Reset stavu
    fun resetSaveResult() {
        _saveResult.value = SaveResult.Idle
        Log.d(TAG,"resetSaveResult: Stav resetován na Idle.")
    }
}