package cz.davidfryda.odectyapp.ui.profile

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import cz.davidfryda.odectyapp.data.UserData
import cz.davidfryda.odectyapp.ui.user.SaveResult
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class ProfileViewModel : ViewModel() {
    private val db = Firebase.firestore
    private val currentUser = Firebase.auth.currentUser

    private val _userData = MutableLiveData<UserData>()
    val userData: LiveData<UserData> = _userData

    private val _saveResult = MutableLiveData<SaveResult>()
    val saveResult: LiveData<SaveResult> = _saveResult

    // Funkce pro načtení dat aktuálního uživatele
    fun loadUserData() {
        if (currentUser == null) return
        db.collection("users").document(currentUser.uid).get()
            .addOnSuccessListener { document ->
                document.toObject(UserData::class.java)?.let {
                    _userData.value = it
                }
            }
    }

    // Funkce pro uložení/aktualizaci dat
    fun saveOrUpdateUser(name: String, surname: String, address: String) {
        viewModelScope.launch {
            _saveResult.value = SaveResult.Loading
            if (currentUser == null) {
                _saveResult.value = SaveResult.Error("Uživatel není přihlášen.")
                return@launch
            }

            val userMap = mapOf(
                "name" to name,
                "surname" to surname,
                "address" to address,
                "uid" to currentUser.uid
            )

            try {
                // Metoda .set() chytře funguje jako "vytvoř, pokud neexistuje, jinak přepiš"
                db.collection("users").document(currentUser.uid).set(userMap).await()
                _saveResult.value = SaveResult.Success
            } catch (e: Exception) {
                _saveResult.value = SaveResult.Error(e.message ?: "Chyba při ukládání.")
            }
        }
    }
}
