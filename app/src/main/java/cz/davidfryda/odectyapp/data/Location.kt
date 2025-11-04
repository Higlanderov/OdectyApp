package cz.davidfryda.odectyapp.data

import java.util.Date

data class Location(
    val id: String = "",
    val userId: String = "",
    val name: String = "",
    val address: String = "",
    val note: String = "",
    val isDefault: Boolean = false,
    val createdAt: Date = Date(),
    val meterCount: Int = 0 // Počet měřáků na tomto místě (computed field)
) {
    // Pomocná metoda pro validaci
    fun isValid(): Boolean {
        return name.isNotBlank() && address.isNotBlank()
    }

    // Pomocná metoda pro zkrácený název (pro TabLayout)
    fun getShortName(): String {
        return if (name.length > 15) {
            name.take(13) + "..."
        } else {
            name
        }
    }
}