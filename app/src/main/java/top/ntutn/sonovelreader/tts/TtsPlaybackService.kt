package top.ntutn.sonovelreader.tts

import android.app.Service
import android.content.Intent
import android.os.IBinder
import top.ntutn.sonovelreader.SoNovelReaderApplication
import top.ntutn.sonovelreader.data.ReaderSettings
import timber.log.Timber

/**
 * 朗读前台服务。仅负责生命周期、Intent 路由与组件协调：
 * 实际焦点管理交给 [AudioFocusController]，通知/MediaSession 交给 [TtsNotificationController]，
 * 合成与播放交给 [TtsPlaybackEngine]。
 */
class TtsPlaybackService : Service() {
    private val container get() = (application as SoNovelReaderApplication).container
    private val manager get() = container.ttsPlaybackManager

    private lateinit var notification: TtsNotificationController
    private lateinit var audioFocus: AudioFocusController
    private lateinit var engine: TtsPlaybackEngine

    override fun onCreate() {
        super.onCreate()
        notification = TtsNotificationController(
            service = this,
            onPlay = { engine.resume() },
            onPause = { engine.pause(false) },
            onStop = { engine.stop() },
        )
        audioFocus = AudioFocusController(
            context = this,
            onFocusChange = { engine.onAudioFocusChange(it) },
            onNoisy = { engine.pause(false) },
        )
        engine = TtsPlaybackEngine(
            context = this,
            container = container,
            audioFocus = audioFocus,
            onState = ::onEngineState,
        )
        audioFocus.register()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Timber.i("onStartCommand action=%s", intent?.action)
        when (intent?.action) {
            ACTION_PLAY -> {
                val bookId = intent.getStringExtra(EXTRA_BOOK_ID) ?: return START_NOT_STICKY
                val chapter = intent.getIntExtra(EXTRA_CHAPTER_INDEX, 0)
                val fraction = intent.getFloatExtra(EXTRA_CHAPTER_FRACTION, 0f)
                notification.startForeground(TtsPlaybackState(TtsPlaybackStatus.PREPARING, bookId))
                engine.start(bookId, chapter, fraction)
            }
            ACTION_PAUSE -> engine.pause(false)
            ACTION_RESUME -> {
                notification.startForeground(manager.state.value)
                engine.resume()
            }
            ACTION_STOP -> {
                Timber.i("收到 STOP 来源=%s", intent.getStringExtra(EXTRA_SOURCE) ?: "App内部")
                engine.stop()
            }
            ACTION_UPDATE_SETTINGS -> engine.updateSettings(intent.readSettings(ReaderSettings()))
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        if (::engine.isInitialized) engine.release()
        if (::audioFocus.isInitialized) audioFocus.unregister()
        if (::notification.isInitialized) notification.release()
        super.onDestroy()
    }

    private fun onEngineState(state: TtsPlaybackState) {
        manager.publish(state)
        notification.update(state)
        if (state.status in TERMINAL_STATUSES) {
            notification.stopForeground()
            stopSelf()
        }
    }

    private fun Intent.readSettings(fallback: ReaderSettings): ReaderSettings = fallback.copy(
        ttsRate = getFloatExtra(EXTRA_RATE, fallback.ttsRate).coerceIn(0.5f, 2f),
        ttsPitch = getFloatExtra(EXTRA_PITCH, fallback.ttsPitch).coerceIn(0.5f, 2f),
        ttsVoiceName = if (hasExtra(EXTRA_VOICE)) getStringExtra(EXTRA_VOICE) else fallback.ttsVoiceName,
    )

    companion object {
        const val ACTION_PLAY = "top.ntutn.sonovelreader.tts.PLAY"
        const val ACTION_PAUSE = "top.ntutn.sonovelreader.tts.PAUSE"
        const val ACTION_RESUME = "top.ntutn.sonovelreader.tts.RESUME"
        const val ACTION_STOP = "top.ntutn.sonovelreader.tts.STOP"
        const val ACTION_UPDATE_SETTINGS = "top.ntutn.sonovelreader.tts.UPDATE_SETTINGS"
        const val EXTRA_BOOK_ID = "book_id"
        const val EXTRA_CHAPTER_INDEX = "chapter_index"
        const val EXTRA_CHAPTER_FRACTION = "chapter_fraction"
        const val EXTRA_RATE = "rate"
        const val EXTRA_PITCH = "pitch"
        const val EXTRA_VOICE = "voice"
        const val EXTRA_SOURCE = "source"
        private val TERMINAL_STATUSES = setOf(
            TtsPlaybackStatus.IDLE,
            TtsPlaybackStatus.COMPLETED,
            TtsPlaybackStatus.ERROR,
        )
    }
}
