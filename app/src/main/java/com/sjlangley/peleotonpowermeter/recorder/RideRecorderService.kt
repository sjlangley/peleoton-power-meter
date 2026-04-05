package com.sjlangley.peleotonpowermeter.recorder

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import com.sjlangley.peleotonpowermeter.PeleotonPowerMeterApp
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

    override fun onCreate() {
        super.onCreate()
        recorderSessionStateStore =
            recorderSessionStateStoreOverride ?: (application as PeleotonPowerMeterApp).recorderSessionStateStore
        recorderSessionController = recorderSessionController()
        ensureNotificationChannel()
        // The current recorder is a deterministic demo loop, so keep it inside
        // Android's short-lived foreground-service bucket until real BLE
        // ingestion lands and justifies connected-device privileges.
        // TODO: When the BLE-backed recorder ships, switch the real recording
        // path to FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE after the app has
        // the required Bluetooth runtime permissions, while keeping the demo
        // emulator flow on shortService.
        startForeground(NOTIFICATION_ID, buildNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SHORT_SERVICE)
        serviceScope.launch {
            recorderSessionController.sessionState.collectLatest { sessionState ->
                recorderSessionStateStore.publish(sessionState)
                if (sessionState is RecorderSessionState.Completed) {
                    stopSelf()
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_DEMO_RIDE ->
                serviceScope.launch {
                    recorderSessionController.startDemoRide()
                }

            ACTION_TOGGLE_PEDAL_DROPOUT ->
                serviceScope.launch {
                    recorderSessionController.togglePedalDropout()
                    updateNotification()
                }

            ACTION_FINISH_RIDE ->
                serviceScope.launch {
                    recorderSessionController.finishRide()
                    stopSelf(startId)
                }
        }

        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        (recorderSessionController as? DemoRecorderSessionController)?.cancel()
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

    private fun recorderSessionController(): RecorderSessionController =
        recorderSessionControllerOverride
            ?: DemoRecorderSessionController((application as PeleotonPowerMeterApp).rideStore)

    companion object {
        const val CHANNEL_ID = "ride-recorder"
        const val NOTIFICATION_ID = 1001
        const val ACTION_START_DEMO_RIDE = "com.sjlangley.peleotonpowermeter.action.START_DEMO_RIDE"
        const val ACTION_TOGGLE_PEDAL_DROPOUT = "com.sjlangley.peleotonpowermeter.action.TOGGLE_PEDAL_DROPOUT"
        const val ACTION_FINISH_RIDE = "com.sjlangley.peleotonpowermeter.action.FINISH_RIDE"
        internal var recorderSessionControllerOverride: RecorderSessionController? = null
        internal var recorderSessionStateStoreOverride: RecorderSessionStateStore? = null

        fun startIntent(context: android.content.Context): Intent =
            Intent(context, RideRecorderService::class.java).setAction(ACTION_START_DEMO_RIDE)

        fun toggleDropoutIntent(context: android.content.Context): Intent =
            Intent(context, RideRecorderService::class.java).setAction(ACTION_TOGGLE_PEDAL_DROPOUT)

        fun finishIntent(context: android.content.Context): Intent =
            Intent(context, RideRecorderService::class.java).setAction(ACTION_FINISH_RIDE)
    }
}
