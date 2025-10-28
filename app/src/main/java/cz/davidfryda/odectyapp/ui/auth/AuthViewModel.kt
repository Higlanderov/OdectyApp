package cz.davidfryda.odectyapp.ui.auth

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class AuthViewModel : ViewModel() {
    private val auth: FirebaseAuth = Firebase.auth
    private val db = Firebase.firestore
    private val _authResult = MutableLiveData<AuthResult>()
    val authResult: LiveData<AuthResult> = _authResult

    // NOVÁ FUNKCE pro přihlášení přes Google
    fun signInWithGoogle(idToken: String) {
        viewModelScope.launch {
            _authResult.value = AuthResult.Loading
            try {
                val credential = GoogleAuthProvider.getCredential(idToken, null)
                val authResult = auth.signInWithCredential(credential).await()
                val user = authResult.user

                if (user != null) {
                    // Zjistíme, jestli je to úplně nový uživatel
                    val isNewUser = authResult.additionalUserInfo?.isNewUser ?: false

                    // Zkontrolujeme roli v databázi
                    val userDoc = db.collection("users").document(user.uid).get().await()
                    val isMaster = userDoc.getString("role") == "master"

                    _authResult.value = AuthResult.Success(user, isMaster, isNewUser)
                } else {
                    throw IllegalStateException("Uživatel nebyl po přihlášení nalezen.")
                }
            } catch (e: Exception) {
                _authResult.value = AuthResult.Error(e.message ?: "Neznámá chyba.")
            }
        }
    }

    fun login(email: String, pass: String) {
        viewModelScope.launch {
            _authResult.value = AuthResult.Loading
            try {
                val authResult = auth.signInWithEmailAndPassword(email, pass).await()
                val user = authResult.user
                if (user != null) {
                    val userDoc = db.collection("users").document(user.uid).get().await()
                    val isMaster = userDoc.getString("role") == "master"
                    // OPRAVENO: Přidán třetí parametr 'isNewUser = false'
                    _authResult.value = AuthResult.Success(user, isMaster, false)
                } else {
                    _authResult.value = AuthResult.Error("Nepodařilo se získat informace o uživateli.")
                }
            } catch (e: Exception) {
                _authResult.value = AuthResult.Error(e.message ?: "Neznámá chyba.")
            }
        }
    }

    fun register(email: String, pass: String) {
        viewModelScope.launch {
            _authResult.value = AuthResult.Loading
            try {
                val result = auth.createUserWithEmailAndPassword(email, pass).await()

                if (result.user != null) {
                    val newUser = result.user!!

                    // --- ✨ OPRAVENÁ DATA PRO NOVÉHO UŽIVATELE ---
                    // HashMap uživatele NESMÍ obsahovat pole "uid",
                    // protože ID dokumentu je samo o sobě UID.
                    val userData = hashMapOf(
                        "uid" to newUser.uid,
                        "email" to newUser.email,
                        "name" to "",
                        "surname" to "",
                        "address" to "",
                        "phoneNumber" to "",
                        "note" to "",
                        "fcmToken" to "", // Přidáno chybějící pole (pro čistotu)
                        "role" to "user",
                        "isDisabled" to false
                    )

                    // Uložíme dokument do Firestore
                    db.collection("users").document(newUser.uid).set(userData).await()

                    _authResult.value = AuthResult.Success(newUser, false, true)

                } else {
                    _authResult.value = AuthResult.Error("Nepodařilo se získat informace o uživateli po registraci.")
                }

            } catch (e: Exception) {
                _authResult.value = AuthResult.Error(e.message ?: "Neznámá chyba.")
            }
        }
    }
}