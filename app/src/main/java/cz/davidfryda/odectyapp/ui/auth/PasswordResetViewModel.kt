package cz.davidfryda.odectyapp.ui.auth

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.tasks.await

class PasswordResetViewModel : ViewModel() {

    private val auth = Firebase.auth
    private val tag = "PasswordResetViewModel"

    private val _resetState = MutableLiveData<ResetState>(ResetState.Idle)
    val resetState: LiveData<ResetState> = _resetState

    suspend fun sendPasswordResetEmail(email: String) {
        if (email.isBlank()) {
            _resetState.value = ResetState.Error("E-mail nesmí být prázdný.")
            return
        }

        _resetState.value = ResetState.Loading
        try {
            auth.sendPasswordResetEmail(email).await()
            Log.d(tag, "Password reset email sent successfully to $email")
            _resetState.value = ResetState.Success
        } catch (e: Exception) {
            Log.e(tag, "Error sending password reset email", e)
            val errorMessage = when (e) {
                is com.google.firebase.auth.FirebaseAuthInvalidUserException -> "Uživatel s tímto e-mailem neexistuje."
                else -> e.message ?: "Neznámá chyba."
            }
            _resetState.value = ResetState.Error(errorMessage)
        }
    }

    fun resetStateToIdle() {
        _resetState.value = ResetState.Idle
    }
}

sealed class ResetState {
    object Idle : ResetState()
    object Loading : ResetState()
    object Success : ResetState()
    data class Error(val message: String) : ResetState()
}