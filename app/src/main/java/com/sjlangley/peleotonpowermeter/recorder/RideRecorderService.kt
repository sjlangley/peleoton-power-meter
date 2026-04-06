package com.sjlangley.peleotonpowermeter.recorder

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import com.sjlangley.peleotonpowermeter.PeleotonPowerMeterApp
import com.sjlangley.peleotonpowermeter.setup.RememberedDeviceStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class RideRecorderService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var recorderSessionController: RecorderSessionController
    private lateinit var recorderSessionStateStore: RecorderSessionStateStore
    private lateinit var rememberedDeviceStore: RememberedDeviceStore
    private var controllerInitialized = false

    override fun onCreate() {
        super.onCreate()
        recorderSessionStateStore =
            recorderSessionStateStoreOverride ?: (application as PeleotonPowerMeterApp).recorderSessionStateStore
        rememberedDeviceStore =
            rememberedDeviceStoreOverride ?: (application as PeleotonPowerMeterApp).rememberedDeviceStore
        // Note: recorderSessionController is created lazily in onStartCommand
        // based on the action (demo vs real ride)
        ensureNotificationChannel()
        // The current recorder is a deterministic demo loop, so keep it inside
        // Android's short-lived foreground-service bucket until real BLE
        // ingestion lands and justifies connected-device privileges.
        // TODO: When the BLE-backed recorder ships, switch the real recording
        // path to FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE after the app has
        // the required Bluetooth runtime permissions, while keeping the demo
        // emulator flow on shortService.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, buildNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SHORT_SERVICE)
        } else {
            startForeground(NOTIFICATION_ID, buildNotification())
        }
    }

    private fun initializeControllerIfNeeded(useDemoController: Boolean) {
        if (!controllerInitialized) {
            recorderSessionController = recorderSessionController(useDemoController)
            controllerInitialized = true
            // Start collecting state changes
            serviceScope.launch {
                recorderSessionController.sessionState.collectLatest { sessionState ->
                    recorderSessionStateStore.publish(sessionState)
                    if (sessionState is RecorderSessionState.Completed) {
                        stopSelf()
                    }
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action

        // Initialize controller based on action before using it
        when (action) {
            ACTION_START_DEMO_RIDE -> {
                initializeControllerIfNeeded(useDemoController = true)
                serviceScope.launch {
                    recorderSessionController.startDemoRide()
                }
            }

            ACTION_START_RIDE -> {
                initializeControllerIfNeeded(useDemoController = false)
                serviceScope.launch {
                    recorderSessionController.startRide()
                }
            }

            ACTION_TOGGLE_PEDAL_DROPOUT -> {
                // Only works with demo controller - if already initialized, use it
                if (controllerInitialized) {
                    serviceScope.launch {
                        recorderSessionController.togglePedalDropout()
                        updateNotification()
                    }
                }
            }

            ACTION_FINISH_RIDE -> {
                // Work with any controller if initialized
                if (controllerInitialized) {
                    serviceScope.launch {
                        recorderSessionController.finishRide()
                        stopSelf(startId)
                    }
                }
            }
        }

        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        if (controllerInitialized) {
            // Cancel the controller if it's a demo controller with cancellation support
            (recorderSessionController as? DemoRecorderSessionController)?.cancel()
        }
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun ensureNotificationChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Ride recording",
            NotificationManager.IMPORTANCE_LOW,
        )
        manager.createNotificationChannel(channel)
    }

    @Suppress("DEPRECATION")
    private fun buildNotification(): Notification =
        Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("Recording ride")
            .setContentText("Power, cadence, and heart rate are being recorded.")
            .setOngoing(true)
            .build()

    private fun updateNotification() {
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification())
    }

    private fun recorderSessionController(useDemoController: Boolean): RecorderSessionController =
        recorderSessionControllerOverride
            ?: RecorderSessionControllerFactory.create(
                context = applicationContext,
                rideStore = (application as PeleotonPowerMeterApp).rideStore,
                rememberedDeviceStore = rememberedDeviceStore,
                useDemoController = useDemoController,
            )

    companion object {
        const val CHANNEL_ID = "ride-recorder"
        const val NOTIFICATION_ID = 1001
        const val ACTION_START_RIDE = "com.sjlangley.peleotonpowermeter.action.START_RIDE"
        const val ACTION_START_DEMO_RIDE = "com.sjlangley.peleotonpowermeter.action.START_DEMO_RIDE"
        const val ACTION_TOGGLE_PEDAL_DROPOUT = "com.sjlangley.peleotonpowermeter.action.TOGGLE_PEDAL_DROPOUT"
        const val ACTION_FINISH_RIDE = "com.sjlangley.peleotonpowermeter.action.FINISH_RIDE"
        internal var recorderSessionControllerOverride: RecorderSessionController? = null
        internal var recorderSessionStateStoreOverride: RecorderSessionStateStore? = null
        internal var rememberedDeviceStoreOverride: RememberedDeviceStore? = null

        fun startRideIntent(context: android.content.Context): Intent =
            Intent(context, RideRecorderService::class.java).setAction(ACTION_START_RIDE)

        fun startDemoRideIntent(context: android.content.Context): Intent =
            Intent(context, RideRecorderService::class.java).setAction(ACTION_START_DEMO_RIDE)

        // Deprecated: Use startRideIntent() or startDemoRideIntent()
        @Deprecated("Use startRideIntent() for normal rides or startDemoRideIntent() for demo rides")
        fun startIntent(context: android.content.Context): Intent =
            Intent(context, RideRecorderService::class.java).setAction(ACTION_START_DEMO_RIDE)

        fun toggleDropoutIntent(context: android.content.Context): Intent =
            Intent(context, RideRecorderService::class.java).setAction(ACTION_TOGGLE_PEDAL_DROPOUT)

        fun finishIntent(context: android.content.Context): Intent =
            Intent(context, RideRecorderService::class.java).setAction(ACTION_FINISH_RIDE)
    }
}
