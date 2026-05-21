package com.rechoraccoon.oscraccoon

import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class NowPlaying(
    val title: String = "",
    val artist: String = "",
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val isPlaying: Boolean = false
)

class MediaListenerService : NotificationListenerService() {

    companion object {
        private val _nowPlaying = MutableStateFlow(NowPlaying())
        val nowPlaying: StateFlow<NowPlaying> = _nowPlaying

        private var instance: MediaListenerService? = null

        fun getInstance(): MediaListenerService? = instance

        fun isRunning() = instance != null
    }

    private var mediaSessionManager: MediaSessionManager? = null
    private var activeController: MediaController? = null

    private val sessionListener = MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
        updateActiveController(controllers)
    }

    private val controllerCallback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) {
            refreshNowPlaying()
        }

        override fun onPlaybackStateChanged(state: PlaybackState?) {
            refreshNowPlaying()
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        mediaSessionManager = getSystemService(MEDIA_SESSION_SERVICE) as MediaSessionManager
        try {
            val controllers = mediaSessionManager?.getActiveSessions(
                android.content.ComponentName(this, MediaListenerService::class.java)
            )
            updateActiveController(controllers)
            mediaSessionManager?.addOnActiveSessionsChangedListener(
                sessionListener,
                android.content.ComponentName(this, MediaListenerService::class.java)
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        activeController?.unregisterCallback(controllerCallback)
        mediaSessionManager?.removeOnActiveSessionsChangedListener(sessionListener)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        refreshNowPlaying()
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        refreshNowPlaying()
    }

    private fun updateActiveController(controllers: List<MediaController>?) {
        activeController?.unregisterCallback(controllerCallback)
        activeController = controllers?.firstOrNull { controller ->
            controller.playbackState?.state == PlaybackState.STATE_PLAYING
        } ?: controllers?.firstOrNull()
        activeController?.registerCallback(controllerCallback)
        refreshNowPlaying()
    }

    private fun refreshNowPlaying() {
        val controller = activeController
        if (controller == null) {
            _nowPlaying.value = NowPlaying()
            return
        }

        val metadata = controller.metadata
        val state = controller.playbackState

        val title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: ""
        val artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
            ?: ""
        val duration = metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L
        val position = state?.position ?: 0L
        val isPlaying = state?.state == PlaybackState.STATE_PLAYING

        _nowPlaying.value = NowPlaying(
            title = title,
            artist = artist,
            positionMs = position,
            durationMs = duration,
            isPlaying = isPlaying
        )
    }

    fun pollPosition() {
        refreshNowPlaying()
    }
}
