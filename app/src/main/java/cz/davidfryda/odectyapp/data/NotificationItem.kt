package cz.davidfryda.odectyapp.data

import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

data class NotificationItem(
    val id: String = "",
    val message: String = "",
    val read: Boolean = false,
    @ServerTimestamp
    val timestamp: Date? = null,
    val readingId: String? = null,
    val userId: String? = null,
    val meterId: String? = null,
    val meterType: String? = null,
    // NOVÁ POLE pro různé typy notifikací
    val type: String? = null,           // "new_reading" nebo "user_registered"
    val userName: String? = null,        // Jméno uživatele (pro registraci)
    val userAddress: String? = null,     // Adresa uživatele (pro registraci)
    val userEmail: String? = null,       // Email uživatele (pro registraci)
    val meterName: String? = null        // Název měřáku (pro odečty)
)