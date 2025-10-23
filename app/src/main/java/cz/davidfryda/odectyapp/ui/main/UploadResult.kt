package cz.davidfryda.odectyapp.ui.main

sealed class UploadResult {
    object Success : UploadResult()
    data class Error(val message: String) : UploadResult()
    object Loading : UploadResult()
    object Idle : UploadResult()
}
