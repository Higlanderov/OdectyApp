package cz.davidfryda.odectyapp.ui.profile

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import cz.davidfryda.odectyapp.data.UserData
import cz.davidfryda.odectyapp.ui.user.SaveResult // Používáme SaveResult
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class ProfileViewModel : ViewModel() {

    private val db = Firebase.firestore
    private val auth = Firebase.auth
    private val tag = "ProfileViewModel"

    private val _userData = MutableLiveData<UserData>()
    val userData: LiveData<UserData> = _userData

    private val _saveResult = MutableLiveData<SaveResult>()
    val saveResult: LiveData<SaveResult> = _saveResult

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    /**
     * Načte data aktuálně přihlášeného uživatele.
     */
    fun loadUserData() {
        val userId = auth.currentUser?.uid ?: return
        _isLoading.value = true
        db.collection("users").document(userId).get()
            .addOnSuccessListener { document ->
                document.toObject(UserData::class.java)?.let {
                    _userData.value = it
                }
                _isLoading.value = false
            }
            .addOnFailureListener { e ->
                Log.e(tag, "Chyba při načítání dat uživatele", e)
                _isLoading.value = false
            }
    }

    /**
     * Vytvoří nebo aktualizuje data uživatele v databázi.
     * Tuto funkci volá UserInfoFragment po registraci.
     */
    fun saveOrUpdateUser(name: String, surname: String, address: String, phone: String) {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            _saveResult.value = SaveResult.Error("Uživatel není přihlášen")
            return
        }

        _saveResult.value = SaveResult.Loading
        viewModelScope.launch {
            try {
                val userDocRef = db.collection("users").document(currentUser.uid)

                // Připravíme data
                val userData = hashMapOf(
                    "uid" to currentUser.uid,
                    "email" to currentUser.email!!,
                    "name" to name,
                    "surname" to surname,
                    "address" to address,
                    "phoneNumber" to phone,
                    "role" to "user", // Výchozí role
                    "isDisabled" to false,
                    "createdAt" to FieldValue.serverTimestamp() // Nastaví se jen při vytvoření
                )

                // Použijeme SetOptions.merge(), aby se data vytvořila
                // (pokud neexistují) nebo aktualizovala (pokud existují).
                // Tím pokryjeme i případné selhání při registraci přes Google.
                userDocRef.set(userData, SetOptions.merge()).await()

                _saveResult.value = SaveResult.Success
            } catch (e: Exception) {
                Log.e(tag, "Chyba při ukládání dat uživatele", e)
                _saveResult.value = SaveResult.Error(e.message ?: "Neznámá chyba")
            }
        }
    }

    fun resetSaveResult() {
        _saveResult.value = SaveResult.Idle
    }
}