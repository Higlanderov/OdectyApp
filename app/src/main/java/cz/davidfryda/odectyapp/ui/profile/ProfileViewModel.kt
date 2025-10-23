package cz.davidfryda.odectyapp.ui.profile

import android.util.Log // Import pro logování
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
    private val TAG = "ProfileViewModel"

    private val _userData = MutableLiveData<UserData>()
    val userData: LiveData<UserData> = _userData

    // Inicializujeme na Idle
    private val _saveResult = MutableLiveData<SaveResult>(SaveResult.Idle)
    val saveResult: LiveData<SaveResult> = _saveResult

    // Funkce pro načtení dat aktuálního uživatele
    fun loadUserData() {
        val user = currentUser
        if (user == null) {
            Log.w(TAG, "loadUserData: Aktuální uživatel je null.")
            return
        }
        val userId = user.uid
        Log.d(TAG, "loadUserData: Načítám data pro uživatele $userId")
        db.collection("users").document(userId).get()
            .addOnSuccessListener { document ->
                document.toObject(UserData::class.java)?.let {
                    _userData.value = it
                    Log.d(TAG, "loadUserData: Data uživatele načtena úspěšně.")
                } ?: run {
                    Log.w(TAG, "loadUserData: Dokument uživatele $userId nenalezen nebo je prázdný.")
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "loadUserData: Chyba při načítání dat uživatele.", e)
            }
    }

    // Funkce pro uložení/aktualizaci dat
    fun saveOrUpdateUser(name: String, surname: String, address: String) {
        viewModelScope.launch {
            _saveResult.value = SaveResult.Loading
            val user = currentUser
            if (user == null) {
                _saveResult.value = SaveResult.Error("Uživatel není přihlášen.")
                return@launch
            }
            val userId = user.uid

            val userMap = mapOf(
                "name" to name,
                "surname" to surname,
                "address" to address,
                "uid" to userId // UID přidáváme pro případ, že by dokument ještě neexistoval
            )

            try {
                // Metoda .set(userMap, SetOptions.merge()) by byla bezpečnější, pokud by UserData obsahovalo více polí,
                // ale zde .set(userMap) přepíše celý dokument, což je pro tato 3 pole v pořádku.
                db.collection("users").document(userId).set(userMap).await()
                _saveResult.value = SaveResult.Success
                Log.d(TAG, "saveOrUpdateUser: Data uživatele $userId úspěšně uložena.")
                // Aktualizujeme i lokální data, aby se změna projevila ihned
                _userData.value = UserData(userId, name, surname, address)
            } catch (e: Exception) {
                _saveResult.value = SaveResult.Error(e.message ?: "Chyba při ukládání.")
                Log.e(TAG, "saveOrUpdateUser: Chyba při ukládání dat uživatele $userId.", e)
            }
        }
    }

    // NOVÁ METODA: Resetuje stav výsledku ukládání
    fun resetSaveResult() {
        _saveResult.value = SaveResult.Idle
        Log.d(TAG,"resetSaveResult: Stav resetován na Idle.")
    }
}
