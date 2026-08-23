package top.ntutn.sonovelreader.tts

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import androidx.core.app.ServiceCompat
import top.ntutn.sonovelreader.MainActivity
import top.ntutn.sonovelreader.R

/** 朗读通知、MediaSession 与前台服务状态管理。 */
class TtsNotificationController(
    private val service: Service,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onStop: () -> Unit,
) {
    private val notificationManager = service.getSystemService(NotificationManager::class.java)
    private var foregroundStarted = false

    private val mediaSession = MediaSession(service, "SoNovelReaderTts").apply {
        setCallback(object : MediaSession.Callback() {
            override fun onPlay() = onPlay()
            override fun onPause() = onPause()
            override fun onStop() = onStop()
        })
        isActive = true
    }

    init {
        notificationManager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "朗读播放", NotificationManager.IMPORTANCE_LOW),
        )
    }

    /** 首次进入前台；后续刷新用 [update]。 */
    fun startForeground(state: TtsPlaybackState) {
        if (!foregroundStarted) {
            ServiceCompat.startForeground(
                service,
                NOTIFICATION_ID,
                buildNotification(state),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
            )
            foregroundStarted = true
        }
        updateMediaSession(state)
    }

    fun update(state: TtsPlaybackState) {
        updateMediaSession(state)
        if (foregroundStarted) notificationManager.notify(NOTIFICATION_ID, buildNotification(state))
    }

    fun stopForeground() {
        foregroundStarted = false
        ServiceCompat.stopForeground(service, ServiceCompat.STOP_FOREGROUND_REMOVE)
    }

    fun release() {
        mediaSession.release()
    }

    private fun updateMediaSession(state: TtsPlaybackState) {
        val playbackState = when (state.status) {
            TtsPlaybackStatus.PLAYING -> PlaybackState.STATE_PLAYING
            TtsPlaybackStatus.PAUSED -> PlaybackState.STATE_PAUSED
            TtsPlaybackStatus.PREPARING -> PlaybackState.STATE_BUFFERING
            TtsPlaybackStatus.ERROR -> PlaybackState.STATE_ERROR
            else -> PlaybackState.STATE_STOPPED
        }
        mediaSession.setPlaybackState(
            PlaybackState.Builder()
                .setActions(PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_STOP)
                .setState(playbackState, PlaybackState.PLAYBACK_POSITION_UNKNOWN, 1f)
                .build(),
        )
        mediaSession.setMetadata(
            MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, state.bookTitle ?: "拾光阅读")
                .putString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE, state.chapterTitle)
                .putString(MediaMetadata.METADATA_KEY_DISPLAY_DESCRIPTION, state.activeSentence?.text)
                .build(),
        )
    }

    private fun buildNotification(state: TtsPlaybackState): Notification {
        val isPlaying = state.status == TtsPlaybackStatus.PLAYING ||
            state.status == TtsPlaybackStatus.PREPARING
        val toggleAction = if (isPlaying) TtsPlaybackService.ACTION_PAUSE else TtsPlaybackService.ACTION_RESUME
        val toggleLabel = if (isPlaying) "暂停" else "继续"
        val toggleIcon = if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
        val contentIntent = PendingIntent.getActivity(
            service,
            1,
            Intent(service, MainActivity::class.java).apply {
                action = MainActivity.ACTION_OPEN_TTS_BOOK
                putExtra(MainActivity.EXTRA_TTS_BOOK_ID, state.bookId)
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return Notification.Builder(service, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle(state.bookTitle ?: "正在准备朗读")
            .setContentText(state.activeSentence?.text ?: state.chapterTitle ?: "拾光阅读")
            .setContentIntent(contentIntent)
            .setOngoing(state.status !in setOf(TtsPlaybackStatus.ERROR, TtsPlaybackStatus.COMPLETED))
            .setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_TRANSPORT)
            .addAction(
                Notification.Action.Builder(
                    Icon.createWithResource(service, toggleIcon),
                    toggleLabel,
                    servicePendingIntent(toggleAction, 2),
                ).build(),
            )
            .addAction(
                Notification.Action.Builder(
                    Icon.createWithResource(service, android.R.drawable.ic_menu_close_clear_cancel),
                    "停止",
                    servicePendingIntent(TtsPlaybackService.ACTION_STOP, 3, source = "notification"),
                ).build(),
            )
            .setStyle(
                Notification.MediaStyle()
                    .setMediaSession(mediaSession.sessionToken)
                    .setShowActionsInCompactView(0, 1),
            )
            .build()
    }

    private fun servicePendingIntent(action: String, requestCode: Int, source: String? = null): PendingIntent =
        PendingIntent.getService(
            service,
            requestCode,
            Intent(service, TtsPlaybackService::class.java).setAction(action).apply {
                if (source != null) putExtra(TtsPlaybackService.EXTRA_SOURCE, source)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    companion object {
        private const val CHANNEL_ID = "tts_playback"
        private const val NOTIFICATION_ID = 2001
    }
}
