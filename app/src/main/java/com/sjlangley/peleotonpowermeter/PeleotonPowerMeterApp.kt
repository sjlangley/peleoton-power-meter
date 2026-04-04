package com.sjlangley.peleotonpowermeter

import android.app.Application
import android.content.Context
import androidx.room.Room
import com.sjlangley.peleotonpowermeter.data.local.AppDatabase
import com.sjlangley.peleotonpowermeter.data.repo.RideStore
import com.sjlangley.peleotonpowermeter.data.repo.RoomRideStore

open class PeleotonPowerMeterApp : Application() {
    open val rideStore: RideStore by lazy { appRideStore(applicationContext) }

    companion object {
        @Volatile
        private var databaseInstance: AppDatabase? = null

        @Volatile
        private var rideStoreInstance: RideStore? = null

        fun appRideStore(context: Context): RideStore =
            rideStoreInstance ?: synchronized(this) {
                rideStoreInstance ?: RoomRideStore(appDatabase(context).rideDao()).also { store ->
                    rideStoreInstance = store
                }
            }

        private fun appDatabase(context: Context): AppDatabase =
            databaseInstance ?: synchronized(this) {
                databaseInstance
                    ?: Room.databaseBuilder(
                        context.applicationContext,
                        AppDatabase::class.java,
                        DATABASE_NAME,
                    ).build().also { database ->
                        databaseInstance = database
                    }
            }
    }
}

private const val DATABASE_NAME = "peleoton-power-meter.db"
