package org.openmrs.chwsync.android.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        RecordEntity::class,
        OutboxEntity::class,
        ConflictEntity::class,
        SettingEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class ChwSyncDatabase : RoomDatabase() {

    abstract fun records(): RecordDao
    abstract fun outbox(): OutboxDao
    abstract fun conflicts(): ConflictDao
    abstract fun settings(): SettingDao

    companion object {
        const val NAME = "chwsync.db"

        /** Offline-first store: a destructive migration is acceptable pre-1.0. */
        fun build(context: Context): ChwSyncDatabase =
            Room.databaseBuilder(context.applicationContext, ChwSyncDatabase::class.java, NAME)
                .fallbackToDestructiveMigration()
                .build()
    }
}
