package com.sjlangley.peleotonpowermeter.recorder

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder

class RideRecorderService : Service() {
    override fun onCreate() {
        super.onCreate()
        ensureNotificationChannel()
        // Recording must be anchored in a foreground service so the app can be
        // trusted to keep capturing while the rider glances elsewhere.
        startForeground(NOTIFICATION_ID, buildNotification())
    }

    override fun onBind(intent: Intent?): IBinder? = null

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

    companion object {
        const val CHANNEL_ID = "ride-recorder"
        const val NOTIFICATION_ID = 1001
    }
}
