package com.sjlangley.peleotonpowermeter.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface RideDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSession(session: RideSessionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSamples(samples: List<RideSampleEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSummary(summary: DerivedSummaryEntity)

    @Query("SELECT * FROM ride_sessions WHERE rideId = :rideId")
    suspend fun session(rideId: String): RideSessionEntity?

    @Query("SELECT * FROM ride_samples WHERE rideId = :rideId ORDER BY timestampEpochSeconds ASC")
    suspend fun samplesForRide(rideId: String): List<RideSampleEntity>

    @Query("SELECT * FROM derived_summaries WHERE rideId = :rideId")
    suspend fun summary(rideId: String): DerivedSummaryEntity?
}
