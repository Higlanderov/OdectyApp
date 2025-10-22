package cz.davidfryda.odectyapp.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "offline_readings")
data class OfflineReading(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val userId: String,
    val meterId: String,
    val localPhotoPath: String, // Cesta k fotce uložené v telefonu
    val finalValue: Double,
    val timestamp: Long = System.currentTimeMillis()
)