/*
 * Velune - Recognition Foreground Service.
 * Runs music recognition with a persistent notification.
 * Ported from Echo Music (GPL-3.0).
 */

package com.nikhil.yt.recognition

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.BitmapFactory
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.nikhil.yt.MainActivity
import com.nikhil.yt.R
import com.nikhil.yt.recognition.models.RecognitionResult
import com.nikhil.yt.recognition.models.RecognitionStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import java.net.HttpURLConnection
import java.net.URL

class RecognitionForegroundService : Service() {
    private val serviceScope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())
    private var recognitionJob: Job? = null
    private var statusJob: Job? = null
    private var keepNotificationOnStop = false
    private var terminalStateHandled = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createNotificationChannel()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Timber.tag(TAG).d("onStartCommand: flags=%d, startId=%d", flags, startId)
        if (!startInForeground()) return START_NOT_STICKY
        startRecognitionIfNeeded()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        Timber.tag(TAG).d("Service destroyed (keepNotification=%b)", keepNotificationOnStop)
        recognitionJob?.cancel()
        statusJob?.cancel()
        serviceScope.cancel()
        if (!keepNotificationOnStop) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        }
        super.onDestroy()
    }

    private fun startInForeground(): Boolean {
        val notification = buildNotification(
            title = getString(R.string.recognize_music),
            contentText = getString(R.string.recognition_notification_listening),
            isTerminal = false,
            contentIntent = null,
            largeIcon = null,
            actionIntent = null,
            actionTitle = null,
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE,
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            return true
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to start foreground service")
            return false
        }
    }

    private fun startRecognitionIfNeeded() {
        if (recognitionJob?.isActive == true) {
            Timber.tag(TAG).d("Recognition already in progress, ignoring duplicate start")
            return
        }
        terminalStateHandled = false
        keepNotificationOnStop = false

        recognitionJob = serviceScope.launch {
            MusicRecognitionService.recognize(this@RecognitionForegroundService)
        }

        statusJob = serviceScope.launch {
            MusicRecognitionService.recognitionStatus.collect { status ->
                if (terminalStateHandled) return@collect
                updateNotification(status)
                if (status is RecognitionStatus.Success || status is RecognitionStatus.Error) {
                    terminalStateHandled = true
                    keepNotificationOnStop = true
                    stopSelf()
                }
            }
        }
    }

    private fun updateNotification(status: RecognitionStatus) {
        val (title, text, isTerminal) = when (status) {
            is RecognitionStatus.Ready -> Triple(
                getString(R.string.recognize_music),
                getString(R.string.recognition_notification_ready),
                false
            )
            is RecognitionStatus.Listening -> Triple(
                getString(R.string.recognize_music),
                getString(R.string.recognition_notification_listening),
                false
            )
            is RecognitionStatus.Processing -> Triple(
                getString(R.string.recognize_music),
                getString(R.string.recognition_notification_processing),
                false
            )
            is RecognitionStatus.Success -> Triple(
                status.result.title,
                status.result.artist,
                true
            )
            is RecognitionStatus.Error -> Triple(
                getString(R.string.recognition_failed),
                status.message,
                true
            )
        }

        val contentIntent = if (isTerminal && status is RecognitionStatus.Success) {
            PendingIntent.getActivity(
                this,
                0,
                Intent(this, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    putExtra(MainActivity.EXTRA_RECOGNITION_RESULT, true)
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        } else null

        val actionIntent = if (isTerminal) {
            PendingIntent.getService(
                this,
                1,
                Intent(this, RecognitionForegroundService::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        } else null

        val actionTitle = if (isTerminal) getString(R.string.recognize_again) else null

        val largeIcon = if (status is RecognitionStatus.Success && status.result.coverUrl != null) {
            loadBitmapFromUrl(status.result.coverUrl)
        } else null

        val notification = buildNotification(
            title = title,
            contentText = text,
            isTerminal = isTerminal,
            contentIntent = contentIntent,
            largeIcon = largeIcon,
            actionIntent = actionIntent,
            actionTitle = actionTitle,
        )

        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun buildNotification(
        title: String,
        contentText: String,
        isTerminal: Boolean,
        contentIntent: PendingIntent?,
        largeIcon: android.graphics.Bitmap?,
        actionIntent: PendingIntent?,
        actionTitle: String?,
    ): android.app.Notification {
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(contentText)
            .setSmallIcon(R.drawable.hearing)
            .setOngoing(!isTerminal)
            .setAutoCancel(isTerminal)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)

        if (contentIntent != null) {
            builder.setContentIntent(contentIntent)
        }

        if (largeIcon != null) {
            builder.setLargeIcon(largeIcon)
        }

        if (actionIntent != null && actionTitle != null) {
            builder.addAction(0, actionTitle, actionIntent)
        }

        if (!isTerminal) {
            builder.setProgress(0, 0, true)
        }

        return builder.build()
    }

    private fun loadBitmapFromUrl(url: String): android.graphics.Bitmap? {
        return try {
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.doInput = true
            connection.connect()
            val input = connection.inputStream
            BitmapFactory.decodeStream(input)
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to load cover bitmap")
            null
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.recognition_notification_channel_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = getString(R.string.recognition_notification_channel_desc)
                setShowBadge(true)
            }
            val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    companion object {
        private const val TAG = "RecognitionService"
        private const val CHANNEL_ID = "velune_recognition_channel"
        private const val NOTIFICATION_ID = 0x5243 // "RC"
    }
}
