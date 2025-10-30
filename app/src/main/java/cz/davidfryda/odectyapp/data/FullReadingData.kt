package cz.davidfryda.odectyapp.data

data class FullReadingData(
    val user: UserData,
    val meter: Meter?,
    val reading: Reading?
)
