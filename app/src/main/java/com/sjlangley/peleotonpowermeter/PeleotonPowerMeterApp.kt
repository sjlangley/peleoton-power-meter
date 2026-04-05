package com.sjlangley.peleotonpowermeter

import android.app.Application
import android.content.Context
import androidx.room.Room
import com.sjlangley.peleotonpowermeter.data.local.AppDatabase
import com.sjlangley.peleotonpowermeter.data.repo.RideStore
import com.sjlangley.peleotonpowermeter.data.repo.RoomRideStore
import com.sjlangley.peleotonpowermeter.fit.AndroidRideFitExporter
import com.sjlangley.peleotonpowermeter.fit.RideFitExporter
import com.sjlangley.peleotonpowermeter.recorder.ForegroundServiceRecorderSessionController
import com.sjlangley.peleotonpowermeter.recorder.RecorderSessionController
import com.sjlangley.peleotonpowermeter.recorder.RecorderSessionStateStore
import com.sjlangley.peleotonpowermeter.setup.AndroidCompanionAssociationStarter
import com.sjlangley.peleotonpowermeter.setup.CompanionAssociationStarter
import com.sjlangley.peleotonpowermeter.setup.RememberedDeviceStore
import com.sjlangley.peleotonpowermeter.setup.SharedPreferencesRememberedDeviceStore

open class PeleotonPowerMeterApp : Application() {
    open val rideStore: RideStore by lazy { appRideStore(applicationContext) }
    open val rememberedDeviceStore: RememberedDeviceStore by lazy {
        appRememberedDeviceStore(applicationContext)
    }
    open val companionAssociationStarter: CompanionAssociationStarter by lazy {
        appCompanionAssociationStarter()
    }
    open val rideFitExporter: RideFitExporter by lazy { appRideFitExporter(applicationContext) }
    open val recorderSessionStateStore: RecorderSessionStateStore by lazy { appRecorderSessionStateStore() }
    open val recorderSessionController: RecorderSessionController by lazy {
        appRecorderSessionController(applicationContext)
    }

    companion object {
        @Volatile
        private var databaseInstance: AppDatabase? = null

        @Volatile
        private var rideStoreInstance: RideStore? = null

        @Volatile
        private var rememberedDeviceStoreInstance: RememberedDeviceStore? = null

        @Volatile
        private var companionAssociationStarterInstance: CompanionAssociationStarter? = null

        @Volatile
        private var rideFitExporterInstance: RideFitExporter? = null

        @Volatile
        private var recorderSessionControllerInstance: RecorderSessionController? = null

        @Volatile
        private var recorderSessionStateStoreInstance: RecorderSessionStateStore? = null

        fun appRideStore(context: Context): RideStore =
            rideStoreInstance ?: synchronized(this) {
                rideStoreInstance ?: RoomRideStore(appDatabase(context).rideDao()).also { store ->
                    rideStoreInstance = store
                }
            }

        fun appRememberedDeviceStore(context: Context): RememberedDeviceStore =
            rememberedDeviceStoreInstance ?: synchronized(this) {
                rememberedDeviceStoreInstance
                    ?: SharedPreferencesRememberedDeviceStore(context.applicationContext).also { store ->
                        rememberedDeviceStoreInstance = store
                    }
            }

        fun appCompanionAssociationStarter(): CompanionAssociationStarter =
            companionAssociationStarterInstance ?: synchronized(this) {
                companionAssociationStarterInstance
                    ?: AndroidCompanionAssociationStarter().also { starter ->
                        companionAssociationStarterInstance = starter
                    }
            }

        fun appRideFitExporter(context: Context): RideFitExporter =
            rideFitExporterInstance ?: synchronized(this) {
                rideFitExporterInstance ?: AndroidRideFitExporter(context.applicationContext).also { exporter ->
                    rideFitExporterInstance = exporter
                }
            }

        fun appRecorderSessionStateStore(): RecorderSessionStateStore =
            recorderSessionStateStoreInstance ?: synchronized(this) {
                recorderSessionStateStoreInstance
                    ?: RecorderSessionStateStore().also { store ->
                        recorderSessionStateStoreInstance = store
                    }
            }

        fun appRecorderSessionController(context: Context): RecorderSessionController =
            recorderSessionControllerInstance ?: synchronized(this) {
                recorderSessionControllerInstance
                    ?: ForegroundServiceRecorderSessionController(
                        context.applicationContext,
                        appRecorderSessionStateStore(),
                    ).also { controller ->
                        recorderSessionControllerInstance = controller
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
