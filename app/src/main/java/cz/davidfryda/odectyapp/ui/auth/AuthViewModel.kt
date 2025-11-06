package cz.davidfryda.odectyapp.ui.auth

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class AuthViewModel : ViewModel() {
    private val auth: FirebaseAuth = Firebase.auth
    private val db = Firebase.firestore
    private val tag = "AuthViewModel" // ✨ PŘIDÁN TAG PRO LOGOVÁNÍ
    private val _authResult = MutableLiveData<AuthResult>()
    val authResult: LiveData<AuthResult> = _authResult

    // ✨ --- TATO FUNKCE CHYBĚLA ---
    /**
     * Přihlásí stávajícího uživatele pomocí e-mailu a hesla.
     */
    fun login(email: String, pass: String) {
        viewModelScope.launch {
            _authResult.value = AuthResult.Loading
            try {
                // Krok 1: Přihlásíme uživatele v Authentication
                val result = auth.signInWithEmailAndPassword(email, pass).await()
                val user = result.user

                if (user != null) {
                    // Krok 2: Zjistíme, jestli je to master
                    val userDoc = db.collection("users").document(user.uid).get().await()
                    val isMaster = userDoc.getString("role") == "master"

                    Log.d(tag, "Přihlášení úspěšné pro: ${user.uid}, Master: $isMaster")

                    _authResult.value = AuthResult.Success(
                        user = user,
                        isMaster = isMaster,
                        isNewUser = false // Při přihlášení to nikdy není nový uživatel
                    )
                } else {
                    _authResult.value = AuthResult.Error("Nepodařilo se získat informace o uživateli po přihlášení.")
                }
            } catch (e: Exception) {
                Log.e(tag, "Chyba při přihlášení", e)
                _authResult.value = AuthResult.Error("Přihlášení selhalo: ${e.message}")
            }
        }
    }
    // ✨ --- KONEC NOVÉ FUNKCE ---

    fun signInWithGoogle(idToken: String) {
        viewModelScope.launch {
            _authResult.value = AuthResult.Loading
            try {
                val credential = GoogleAuthProvider.getCredential(idToken, null)
                val authResult = auth.signInWithCredential(credential).await()
                val user = authResult.user

                if (user != null) {
                    val isNewUser = authResult.additionalUserInfo?.isNewUser ?: false

                    if (isNewUser) {
                        // Logika pro Google Sign-In (v pořádku)
                        val userData = hashMapOf(
                            "uid" to user.uid,
                            "email" to user.email,
                            "name" to  "",
                            "surname" to "",
                            "address" to "",
                            "phoneNumber" to "",
                            "note" to "",
                            "fcmToken" to "",
                            "role" to "user",
                            "isDisabled" to false,
                            "createdAt" to FieldValue.serverTimestamp()
                        )
                        db.collection("users").document(user.uid).set(userData).await()
                    }

                    // Zjistíme, jestli je uživatel master (po přihlášení)
                    val userDoc = db.collection("users").document(user.uid).get().await()
                    val isMaster = userDoc.getString("role") == "master"

                    _authResult.value = AuthResult.Success(
                        user = user,
                        isMaster = isMaster,
                        isNewUser = isNewUser
                    )
                } else {
                    _authResult.value = AuthResult.Error("Nepodařilo se přihlásit přes Google.")
                }
            } catch (e: Exception) {
                _authResult.value = AuthResult.Error(e.message ?: "Neznámá chyba.")
            }
        }
    }

    /**
     * Zaregistruje nového uživatele pomocí e-mailu a hesla.
     * TATO FUNKCE JE OPRAVENÁ: Vytváří POUZE záznam v Authentication.
     */
    fun register(email: String, pass: String) {
        viewModelScope.launch {
            _authResult.value = AuthResult.Loading
            try {
                // Krok 1: Vytvoříme uživatele v Authentication
                val result = auth.createUserWithEmailAndPassword(email, pass).await()

                if (result.user != null) {
                    val newUser = result.user!!

                    // Krok 2: NEPROVÁDÍME ZÁPIS DO DATABÁZE (to udělá UserInfoFragment)

                    Log.d(tag, "Registrace úspěšná pro: ${newUser.uid}")

                    // Krok 3: Oznámíme úspěch a přesměrujeme na UserInfoFragment
                    _authResult.value = AuthResult.Success(
                        user = newUser,
                        isMaster = false,
                        isNewUser = true
                    )

                } else {
                    _authResult.value = AuthResult.Error("Nepodařilo se získat informace o uživateli po registraci.")
                }

            } catch (e: Exception) {
                Log.e(tag, "Chyba při registraci", e)
                _authResult.value = AuthResult.Error(e.message ?: "Neznámá chyba.")
            }
        }
    }
}