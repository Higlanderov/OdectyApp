package cz.davidfryda.odectyapp.data

import com.google.firebase.firestore.PropertyName

data class UserData(
    val uid: String = "",
    val name: String = "",
    val surname: String = "",
    val address: String = "",
    val phoneNumber: String = "",
    val email: String = "",
    val note: String = "",

    @get:PropertyName("isDisabled")
    val isDisabled: Boolean = false
)


