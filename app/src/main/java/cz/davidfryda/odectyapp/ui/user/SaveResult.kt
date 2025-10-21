package cz.davidfryda.odectyapp.ui.user

sealed class SaveResult {
    object Success : SaveResult()
    data class Error(val message: String) : SaveResult()
    object Loading : SaveResult()
}
