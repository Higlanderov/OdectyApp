package cz.davidfryda.odectyapp.ui.main

sealed class ValidationResult {
    object Valid : ValidationResult() // Hodnota je v pořádku, uloženo
    data class WarningHigh(val message: String) : ValidationResult() // Varování - příliš vysoká
    data class WarningLow(val message: String) : ValidationResult() // Varování - příliš nízká
    data class Error(val message: String) : ValidationResult()
}