package cz.davidfryda.odectyapp.data

import com.google.firebase.Timestamp

data class Meter(
    val id: String = "",
    val userId: String = "",
    val locationId: String = "",
    val name: String = "",
    val type: String = "",
    val masterDescription: String? = null,
    val createdAt: Timestamp? = null
)
