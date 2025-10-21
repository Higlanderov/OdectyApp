package cz.davidfryda.odectyapp.ui.master

import cz.davidfryda.odectyapp.data.UserData

data class UserWithStatus(
    val user: UserData,
    val hasReadingForCurrentMonth: Boolean
)
