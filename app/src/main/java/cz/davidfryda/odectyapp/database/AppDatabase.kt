package cz.davidfryda.odectyapp.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

// ✨ OPRAVA: Přidána 'PendingDeletion::class' a zvýšena verze (např. z 1 na 2)
@Database(entities = [OfflineReading::class, PendingDeletion::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {

    abstract fun readingDao(): ReadingDao

    // ✨ OPRAVA: Přidána abstraktní funkce pro nové DAO
    abstract fun pendingDeletionDao(): PendingDeletionDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "odecty_database"
                )
                    // ✨ OPRAVA: Přidána migrace, pokud měníte verzi existující databáze
                    .fallbackToDestructiveMigration() // Jednoduchá migrace, smaže stará data
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}