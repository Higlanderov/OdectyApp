package cz.davidfryda.odectyapp.data

import cz.davidfryda.odectyapp.data.Meter
import cz.davidfryda.odectyapp.data.Reading
import cz.davidfryda.odectyapp.data.UserData

data class FullReadingData(
    val user: UserData,
    val meter: Meter?,
    val reading: Reading?
)
