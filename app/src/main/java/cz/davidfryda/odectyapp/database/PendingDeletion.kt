package cz.davidfryda.odectyapp.database

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entita reprezentující položku ve frontě ke smazání.
 * Ukládáme si, co se má smazat (typ) a potřebná ID.
 */
@Entity(tableName = "pending_deletions")
data class PendingDeletion(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,

    /**
     * Typ entity ke smazání, např. "LOCATION", "METER", "READING".
     * Děláme to rozšiřitelné pro budoucnost.
     */
    val entityType: String,

    /**
     * ID uživatele, pod kterým je entita uložena (pro sestavení cesty).
     */
    val userId: String,

    /**
     * ID samotné entity (např. locationId).
     */
    val entityId: String
)

// Definice typů pro konzistenci
object DeletionTypes {
    const val LOCATION = "LOCATION"
    const val METER = "METER"
    // const val READING = "READING" // Pro budoucí použití
}