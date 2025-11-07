package cz.davidfryda.odectyapp.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface PendingDeletionDao {

    /**
     * Vloží novou žádost o smazání do fronty.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(pendingDeletion: PendingDeletion)

    /**
     * Načte všechny čekající žádosti o smazání.
     */
    @Query("SELECT * FROM pending_deletions")
    suspend fun getAllPendingDeletions(): List<PendingDeletion>

    /**
     * Smaže žádost z fronty (po úspěšném smazání na serveru).
     */
    @Query("DELETE FROM pending_deletions WHERE id = :id")
    suspend fun deleteById(id: Int)
}