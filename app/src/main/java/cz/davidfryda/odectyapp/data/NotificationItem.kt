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
    val meterType: String? = null
)
