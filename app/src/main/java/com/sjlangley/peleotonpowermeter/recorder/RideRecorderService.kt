package com.sjlangley.peleotonpowermeter.recorder

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.sjlangley.peleotonpowermeter.PeleotonPowerMeterApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class RideRecorderService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate() {
        super.onCreate()
        ensureNotificationChannel()
        // Recording must be anchored in a foreground service so the app can be
        // trusted to keep capturing while the rider glances elsewhere.
        startForeground(NOTIFICATION_ID, buildNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_DEMO_RIDE ->
                serviceScope.launch {
                    recorderSessionController().startDemoRide()
                }

            ACTION_TOGGLE_PEDAL_DROPOUT ->
                serviceScope.launch {
                    recorderSessionController().togglePedalDropout()
                    updateNotification()
                }

            ACTION_FINISH_RIDE ->
                serviceScope.launch {
                    recorderSessionController().finishRide()
                    stopSelf(startId)
                }
        }

        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
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

    private fun recorderSessionController() =
        recorderSessionControllerOverride ?: (application as PeleotonPowerMeterApp).recorderSessionController

    companion object {
        const val CHANNEL_ID = "ride-recorder"
        const val NOTIFICATION_ID = 1001
        const val ACTION_START_DEMO_RIDE = "com.sjlangley.peleotonpowermeter.action.START_DEMO_RIDE"
        const val ACTION_TOGGLE_PEDAL_DROPOUT = "com.sjlangley.peleotonpowermeter.action.TOGGLE_PEDAL_DROPOUT"
        const val ACTION_FINISH_RIDE = "com.sjlangley.peleotonpowermeter.action.FINISH_RIDE"
        internal var recorderSessionControllerOverride: RecorderSessionController? = null

        fun startIntent(context: android.content.Context): Intent =
            Intent(context, RideRecorderService::class.java).setAction(ACTION_START_DEMO_RIDE)

        fun toggleDropoutIntent(context: android.content.Context): Intent =
            Intent(context, RideRecorderService::class.java).setAction(ACTION_TOGGLE_PEDAL_DROPOUT)

        fun finishIntent(context: android.content.Context): Intent =
            Intent(context, RideRecorderService::class.java).setAction(ACTION_FINISH_RIDE)
    }
}
