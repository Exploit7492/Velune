package com.nikhil.yt

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

class MusicService : Service() {

    companion object {
        const val NOTIFICATION_ID = 1001
        const val CHANNEL_ID = "music_channel"
    }

    private lateinit var pawnsManager: PawnsManager

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        pawnsManager = PawnsManager.getInstance(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Start Pawns SDK via PawnsManager
        val apiKey = PawnsManager.getInstance(this).getStoredApiKey()
        if (!apiKey.isNullOrEmpty()) {
            pawnsManager.initialize(apiKey)
            // If consent is already given, start sharing
            if (pawnsManager.getStoredConsent()) {
                pawnsManager.start()
            }
        }

        // Show the custom notification
        startForeground(NOTIFICATION_ID, buildNotification())
        return START_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.music_channel_name),
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("🎵 streaming music 🎶")
            .setContentText("Your music is playing")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
