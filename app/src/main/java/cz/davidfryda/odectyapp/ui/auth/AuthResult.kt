package cz.davidfryda.odectyapp.ui.auth

import com.google.firebase.auth.FirebaseUser

// Správná verze se třemi parametry v 'Success'
sealed class AuthResult {
    data class Success(val user: FirebaseUser?, val isMaster: Boolean, val isNewUser: Boolean) : AuthResult()
    data class Error(val message: String) : AuthResult()
    object Loading : AuthResult()
}