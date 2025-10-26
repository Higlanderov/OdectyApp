package cz.davidfryda.odectyapp.data

data class UserData(
    val uid: String = "",
    val name: String = "",
    val surname: String = "",
    val address: String = "",
    val phoneNumber: String = "",
    val email: String = "",
    val note: String = "",
    val isDisabled: Boolean = false
)


