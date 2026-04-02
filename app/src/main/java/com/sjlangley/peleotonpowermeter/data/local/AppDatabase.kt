package com.sjlangley.peleotonpowermeter.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        RideSessionEntity::class,
        RideSampleEntity::class,
        DerivedSummaryEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun rideDao(): RideDao
}
