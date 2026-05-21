package com.rechoraccoon.oscraccoon

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.os.PowerManager
import kotlinx.coroutines.*

class OscForegroundService : Service() {

    companion object {
        const val ACTION_START = "START"
        const val ACTION_STOP = "STOP"
        const val EXTRA_MAIN_TEMPLATE = "main_template"
        const val EXTRA_CYCLING_MESSAGES = "cycling_messages"
        const val EXTRA_CYCLE_INTERVAL = "cycle_interval"
        var isRunning = false

        fun formatTemplate(template: String, nowPlaying: NowPlaying): String {
            val posMin = (nowPlaying.positionMs / 1000) / 60
            val posSec = (nowPlaying.positionMs / 1000) % 60
            val durMin = (nowPlaying.durationMs / 1000) / 60
            val durSec = (nowPlaying.durationMs / 1000) % 60
            val duration = "%d:%02d / %d:%02d".format(posMin, posSec, durMin, durSec)
            val progressBarLength = 12
            val progress = if (nowPlaying.durationMs > 0)
                (nowPlaying.positionMs.toFloat() / nowPlaying.durationMs.toFloat())
            else 0f
            val filled = (progress * progressBarLength).toInt().coerceIn(0, progressBarLength)
            val progressBar = "▓".repeat(filled) + "░".repeat(progressBarLength - filled)
            return template
                .replace("{song}", nowPlaying.title.ifEmpty { "Nothing Playing" })
                .replace("{artist}", nowPlaying.artist.ifEmpty { "Unknown" })
                .replace("{duration}", duration)
                .replace("{progress}", progressBar)
        }
    }

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var wakeLock: PowerManager.WakeLock? = null
    private var mainTemplate = "{song}\nby {artist} | {duration}"
    private var cyclingMessages = listOf<String>()
    private var cycleIntervalSeconds = 5
    private var currentCycleIndex = 0
    private var cycleTickCounter = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                mainTemplate = intent.getStringExtra(EXTRA_MAIN_TEMPLATE)
                    ?: "{song}\nby {artist} | {duration}"
                cyclingMessages = intent.getStringArrayListExtra(EXTRA_CYCLING_MESSAGES)
                    ?: listOf()
                cycleIntervalSeconds = intent.getIntExtra(EXTRA_CYCLE_INTERVAL, 5)
                startForeground(1, buildNotification())
                acquireWakeLock()
                isRunning = true
                startOscLoop()
            }
            ACTION_STOP -> stopSelf()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        serviceScope.cancel()
        wakeLock?.release()
        OscSender.clearChatbox()
    }

    private fun startOscLoop() {
        serviceScope.launch {
            while (isActive) {
                val nowPlaying = MediaListenerService.nowPlaying.value
                MediaListenerService.getInstance()?.pollPosition()
                cycleTickCounter++
                if (cycleTickCounter >= cycleIntervalSeconds) {
                    cycleTickCounter = 0
                    if (cyclingMessages.isNotEmpty()) {
                        currentCycleIndex = (currentCycleIndex + 1) % cyclingMessages.size
                    }
                }
                val mainLine = formatTemplate(mainTemplate, nowPlaying)
                val combined = if (cyclingMessages.isNotEmpty()) {
                    "$mainLine\n${cyclingMessages[currentCycleIndex]}"
                } else mainLine
                OscSender.sendChatboxMessage(combined)
                delay(1000L)
            }
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            "osc_raccoon_service",
            "OSC Raccoon Service",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Keeps OSC Raccoon running while VRChat is active"
        }
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
            .createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        return Notification.Builder(this, "osc_raccoon_service")
            .setContentTitle("OSC Raccoon Running")
            .setContentText("Sending music info to VRChat chatbox")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .build()
    }

    private fun acquireWakeLock() {
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "OSCRaccoon::WakeLock")
        wakeLock?.acquire(10 * 60 * 60 * 1000L)
    }
}
