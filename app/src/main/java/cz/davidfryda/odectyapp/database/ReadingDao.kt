package cz.davidfryda.odectyapp.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ReadingDao {
    @Insert
    suspend fun insert(offlineReading: OfflineReading): Long // Vrátí ID nově vloženého záznamu

    @Query("SELECT * FROM offline_readings WHERE userId = :userId ORDER BY timestamp DESC")
    fun getOfflineReadingsForUser(userId: String): Flow<List<OfflineReading>>

    @Query("DELETE FROM offline_readings WHERE id = :id")
    suspend fun deleteById(id: Int)

    @Query("SELECT * FROM offline_readings WHERE id = :id")
    suspend fun getById(id: Int): OfflineReading?
}
