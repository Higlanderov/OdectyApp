package cz.davidfryda.odectyapp.ui.user

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class UserInfoViewModel : ViewModel() {

    // Získáme přístup k databázi Firestore
    private val db = Firebase.firestore

    private val _saveResult = MutableLiveData<SaveResult>()
    val saveResult: LiveData<SaveResult> = _saveResult

    fun saveUserInfo(name: String, surname: String, address: String) {
        viewModelScope.launch {
            _saveResult.value = SaveResult.Loading

            // Získáme ID aktuálně přihlášeného uživatele. Je to klíčové pro propojení dat.
            val currentUser = Firebase.auth.currentUser
            if (currentUser == null) {
                _saveResult.value = SaveResult.Error("Uživatel není přihlášen.")
                return@launch
            }

            // Připravíme data, která chceme uložit. Použijeme Mapu.
            val userData = hashMapOf(
                "name" to name,
                "surname" to surname,
                "address" to address,
                "uid" to currentUser.uid
            )

            try {
                // Uložíme data do kolekce "users" s dokumentem, jehož ID je shodné s UID uživatele.
                // Tím zajistíme, že každý uživatel má svá data pod svým unikátním klíčem.
                db.collection("users").document(currentUser.uid).set(userData).await()
                _saveResult.value = SaveResult.Success
            } catch (e: Exception) {
                _saveResult.value = SaveResult.Error(e.message ?: "Neznámá chyba při ukládání dat.")
            }
        }
    }
}
