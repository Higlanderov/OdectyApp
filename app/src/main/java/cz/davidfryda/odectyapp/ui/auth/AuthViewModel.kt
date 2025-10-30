package cz.davidfryda.odectyapp.ui.auth

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
    private val _authResult = MutableLiveData<AuthResult>()
    val authResult: LiveData<AuthResult> = _authResult

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
                        val userData = hashMapOf(
                            "uid" to user.uid,
                            "email" to user.email,
                            "name" to "",
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

                    val userDoc = db.collection("users").document(user.uid).get().await()
                    val isMaster = userDoc.getString("role") == "master"

                    _authResult.value = AuthResult.Success(
                        user = user,
                        isMaster = isMaster,
                        isNewUser = isNewUser  // ✨ ZMĚNA: Pojmenovaný parametr
                    )
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
                    _authResult.value = AuthResult.Success(
                        user = user,
                        isMaster = isMaster,
                        isNewUser = false  // ✨ ZMĚNA: Pojmenovaný parametr
                    )
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

                    val userData = hashMapOf(
                        "uid" to newUser.uid,
                        "email" to newUser.email,
                        "name" to "",
                        "surname" to "",
                        "address" to "",
                        "phoneNumber" to "",
                        "note" to "",
                        "fcmToken" to "",
                        "role" to "user",
                        "isDisabled" to false,
                        "createdAt" to FieldValue.serverTimestamp()
                    )

                    db.collection("users").document(newUser.uid).set(userData).await()

                    _authResult.value = AuthResult.Success(
                        user = newUser,
                        isMaster = false,
                        isNewUser = true  // ✨ ZMĚNA: Pojmenovaný parametr
                    )

                } else {
                    _authResult.value = AuthResult.Error("Nepodařilo se získat informace o uživateli po registraci.")
                }

            } catch (e: Exception) {
                _authResult.value = AuthResult.Error(e.message ?: "Neznámá chyba.")
            }
        }
    }
}