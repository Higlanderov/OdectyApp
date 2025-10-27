package cz.davidfryda.odectyapp.data

// Data class reprezentující jeden měřák energie
data class Meter(
    val id: String = "", // ID dokumentu ve Firestore
    val userId: String = "", // ID uživatele, kterému měřák patří
    val name: String = "", // Název měřáku zadaný uživatelem
    val type: String = "", // Typ měřáku (Elektřina, Plyn, Voda)
    val masterDescription: String? = null // Popis přidaný master uživatelem (nepovinný)
)

