package cz.davidfryda.odectyapp.data

import cz.davidfryda.odectyapp.ui.main.Meter
import cz.davidfryda.odectyapp.ui.main.Reading

data class FullReadingData(
    val user: UserData,
    val meter: Meter?,
    val reading: Reading?
)
