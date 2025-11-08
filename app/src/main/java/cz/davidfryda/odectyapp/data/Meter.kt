package cz.davidfryda.odectyapp.data

import com.google.firebase.Timestamp
import java.util.Date

data class Meter(
    val id: String = "",
    val userId: String = "",
    val locationId: String = "",
    val name: String = "",
    val type: String = "",
    val masterDescription: String? = null,
    val createdAt: Timestamp? = null,
    // ✨ NOVÉ: Informace o posledním odečtu (není uloženo v DB, pouze pro zobrazení)
    val lastReading: Reading? = null
)