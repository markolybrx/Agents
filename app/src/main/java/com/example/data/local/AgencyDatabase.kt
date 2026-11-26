package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        ProjectEntity::class,
        PhaseEntity::class,
        TaskEntity::class,
        GeneratedFileEntity::class,
        TokenUsageEntity::class,
        RunLogEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AgencyDatabase : RoomDatabase() {
    abstract fun agencyDao(): AgencyDao

    companion object {
        @Volatile
        private var INSTANCE: AgencyDatabase? = null

        fun getInstance(context: Context): AgencyDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AgencyDatabase::class.java,
                    "agent_desk_studio.db"
                ).fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
