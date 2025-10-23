package cz.davidfryda.odectyapp.ui.user

/**
 * Reprezentuje výsledek operace ukládání nebo jiné asynchronní akce.
 * Zahrnuje i stav Idle pro resetování po zpracování události.
 */
sealed class SaveResult {
    object Success : SaveResult() // Operace proběhla úspěšně
    data class Error(val message: String) : SaveResult() // Nastala chyba
    object Loading : SaveResult() // Operace probíhá
    object Idle : SaveResult() // Výchozí/nečinný stav
}

