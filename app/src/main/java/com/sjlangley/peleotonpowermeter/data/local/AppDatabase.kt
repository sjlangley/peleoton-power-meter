package com.sjlangley.peleotonpowermeter.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        RideSessionEntity::class,
        RideSampleEntity::class,
        DerivedSummaryEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(RideDataConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun rideDao(): RideDao
}
