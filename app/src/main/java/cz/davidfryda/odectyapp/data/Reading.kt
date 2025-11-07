package cz.davidfryda.odectyapp.data

import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

data class Reading(
    val id: String = "",
    val meterId: String = "",
    val userId: String = "",
    val finalValue: Double? = null,
    val photoUrl: String = "",
    val photoStoragePath: String = "",
    @ServerTimestamp
    val timestamp: Date? = null,
    // NOVÉ POLE:
    val editedByAdmin: Boolean = false,
    val isSynced: Boolean = true // Nový příznak
)
