package top.ntutn.sonovelreader.tts

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.os.IBinder
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.core.app.ServiceCompat
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import top.ntutn.sonovelreader.MainActivity
import top.ntutn.sonovelreader.R
import top.ntutn.sonovelreader.SoNovelReaderApplication
import top.ntutn.sonovelreader.data.ParsedBook
import top.ntutn.sonovelreader.data.ReaderContent
import top.ntutn.sonovelreader.data.ReaderLocator
import top.ntutn.sonovelreader.data.ReaderSettings
import timber.log.Timber
import kotlin.time.Duration.Companion.milliseconds

class TtsPlaybackService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val ttsReady = CompletableDeferred<Int>()
    private lateinit var tts: TextToSpeech
    private lateinit var mediaSession: MediaSession
    private lateinit var audioManager: AudioManager
    private lateinit var audioFocusRequest: AudioFocusRequest
    private val container get() = (application as SoNovelReaderApplication).container
    private val manager get() = container.ttsPlaybackManager

    private var bookId: String? = null
    private var book: ParsedBook? = null
    private var content: ReaderContent? = null
    private var chapterIndex = 0
    private var sentences: List<TtsSentence> = emptyList()
    private var sentenceIndex = 0
    private var currentUtteranceId: String? = null
    private var consecutiveErrors = 0
    private var currentSentenceRetries = 0
    private var settings = ReaderSettings()
    private var resumeAfterFocusGain = false
    private var foregroundStarted = false
    private var playbackJob: Job? = null

    private val noisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) pausePlayback(false)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        audioManager = getSystemService(AudioManager::class.java)
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()
        audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(audioAttributes)
            .setOnAudioFocusChangeListener(::onAudioFocusChanged)
            .build()
        mediaSession = MediaSession(this, "SoNovelReaderTts").apply {
            setCallback(object : MediaSession.Callback() {
                override fun onPlay() = resumePlayback()
                override fun onPause() = pausePlayback(false)
                override fun onStop() = stopPlayback(TtsPlaybackStatus.IDLE)
            })
            isActive = true
        }
        tts = TextToSpeech(applicationContext) { status ->
            Timber.i("TTS 引擎初始化 status=%d", status)
            ttsReady.complete(status)
        }.apply {
            setAudioAttributes(audioAttributes)
            setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    scope.launch {
                        if (utteranceId == currentUtteranceId) {
                            consecutiveErrors = 0
                            currentSentenceRetries = 0
                            saveCurrentProgress()
                        }
                    }
                }

                override fun onDone(utteranceId: String?) {
                    scope.launch {
                        if (utteranceId == currentUtteranceId) {
                            sentenceIndex++
                            currentSentenceRetries = 0
                            speakCurrentOrAdvance()
                        }
                    }
                }

                @Deprecated("Deprecated by Android")
                override fun onError(utteranceId: String?) = onError(utteranceId, -1)

                override fun onError(utteranceId: String?, errorCode: Int) {
                    scope.launch {
                        if (utteranceId != currentUtteranceId) {
                            Timber.w("忽略过期的 TTS 错误 utteranceId=%s errorCode=%d", utteranceId, errorCode)
                            return@launch
                        }
                        currentUtteranceId = null
                        handleSynthesisFailure("引擎错误", errorCode)
                    }
                }
            })
        }
        val filter = IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(noisyReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(noisyReceiver, filter)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Timber.i("onStartCommand action=%s", intent?.action)
        when (intent?.action) {
            ACTION_PLAY -> {
                settings = intent.readSettings(settings)
                val requestedBookId = intent.getStringExtra(EXTRA_BOOK_ID) ?: return START_NOT_STICKY
                val requestedChapter = intent.getIntExtra(EXTRA_CHAPTER_INDEX, 0)
                val requestedFraction = intent.getFloatExtra(EXTRA_CHAPTER_FRACTION, 0f)
                ensureForeground(TtsPlaybackState(TtsPlaybackStatus.PREPARING, requestedBookId))
                playbackJob?.cancel()
                playbackJob = scope.launch { startPlayback(requestedBookId, requestedChapter, requestedFraction) }
            }
            ACTION_PAUSE -> pausePlayback(false)
            ACTION_RESUME -> {
                ensureForeground(manager.state.value)
                resumePlayback()
            }
            ACTION_STOP -> {
                Timber.i("收到 STOP 来源=%s", intent.getStringExtra(EXTRA_SOURCE) ?: "App内部")
                stopPlayback(TtsPlaybackStatus.IDLE)
            }
            ACTION_UPDATE_SETTINGS -> {
                settings = intent.readSettings(settings)
                if (manager.state.value.status in setOf(TtsPlaybackStatus.PLAYING, TtsPlaybackStatus.PAUSED)) {
                    if (manager.state.value.status == TtsPlaybackStatus.PLAYING) speakCurrent()
                    else publish(TtsPlaybackStatus.PAUSED)
                }
            }
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        runCatching { unregisterReceiver(noisyReceiver) }
        audioManager.abandonAudioFocusRequest(audioFocusRequest)
        mediaSession.release()
        tts.stop()
        tts.shutdown()
        scope.cancel()
        super.onDestroy()
    }

    private suspend fun startPlayback(requestedBookId: String, requestedChapter: Int, requestedFraction: Float) {
        tts.stop()
        currentUtteranceId = null
        bookId = requestedBookId
        book = null
        content = null
        sentences = emptyList()
        sentenceIndex = 0
        Timber.i("开始朗读 bookId=%s chapter=%d fraction=%f", requestedBookId, requestedChapter, requestedFraction)
        publish(TtsPlaybackStatus.PREPARING, requestedBookId = requestedBookId, sentence = null)
        if (ttsReady.await() != TextToSpeech.SUCCESS) {
            Timber.w("TTS 未就绪，初始化失败")
            fail("系统 TTS 初始化失败，请检查语音引擎")
            return
        }
        try {
            val parsedBook = container.bookRepository.openBook(requestedBookId)
            book = parsedBook
            settings = container.settingsRepository.settings.first()
            chapterIndex = requestedChapter.coerceIn(parsedBook.chapters.indices)
            loadChapter(chapterIndex)
            sentenceIndex = sentenceIndexForProgress(content!!, sentences, requestedFraction).coerceAtLeast(0)
            speakCurrentOrAdvance()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            fail(error.message ?: "无法开始朗读")
        }
    }

    private suspend fun loadChapter(index: Int) {
        val parsedBook = checkNotNull(book)
        chapterIndex = index
        content = container.bookRepository.readChapter(parsedBook, parsedBook.chapters[index])
        sentences = segmentChapter(checkNotNull(content), index, TextToSpeech.getMaxSpeechInputLength())
        sentenceIndex = 0
        Timber.i("加载章节 index=%d 句子数=%d", index, sentences.size)
    }

    private suspend fun speakCurrentOrAdvance() {
        val parsedBook = book ?: return
        while (true) {
            when (ttsQueueBoundary(chapterIndex, parsedBook.chapters.lastIndex, sentenceIndex, sentences.size)) {
                TtsQueueBoundary.SPEAK -> {
                    speakCurrent()
                    return
                }
                TtsQueueBoundary.NEXT_CHAPTER -> loadChapter(chapterIndex + 1)
                TtsQueueBoundary.COMPLETE -> {
                val lastChapter = parsedBook.chapters.last()
                container.progressRepository.save(
                    checkNotNull(bookId),
                    ReaderLocator(lastChapter.href, parsedBook.chapters.lastIndex, 1f),
                )
                stopPlayback(TtsPlaybackStatus.COMPLETED)
                return
                }
            }
        }
    }

    private fun speakCurrent() {
        val sentence = sentences.getOrNull(sentenceIndex) ?: return
        if (audioManager.requestAudioFocus(audioFocusRequest) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            Timber.w("无法获取音频焦点")
            publish(TtsPlaybackStatus.PAUSED, error = "其他应用正在使用音频")
            return
        }
        configureVoice(sentence.text)
        tts.setSpeechRate(settings.ttsRate.coerceIn(0.5f, 2f))
        tts.setPitch(settings.ttsPitch.coerceIn(0.5f, 2f))
        val utteranceId = UUID.randomUUID().toString()
        currentUtteranceId = utteranceId
        Timber.d("朗读 sentenceIndex=%d 句子=[%s]", sentenceIndex, sentence.text)
        publish(TtsPlaybackStatus.PLAYING, sentence = sentence)
        if (tts.speak(sentence.text, TextToSpeech.QUEUE_FLUSH, null, utteranceId) == TextToSpeech.ERROR) {
            Timber.w("tts.speak 同步返回 ERROR utteranceId=%s", utteranceId)
            currentUtteranceId = null
            handleSynthesisFailure("朗读请求被拒绝")
        }
    }

    private fun configureVoice(text: String) {
        val available = tts.voices.orEmpty()
        val selected = settings.ttsVoiceName?.let { name -> available.firstOrNull { it.name == name } }
        val automaticLocale = if (text.codePoints().anyMatch { Character.UnicodeScript.of(it) == Character.UnicodeScript.HAN }) {
            Locale.SIMPLIFIED_CHINESE
        } else {
            Locale.getDefault()
        }
        val automatic = available
            .filter { it.locale.language == automaticLocale.language }
            .sortedBy { it.isNetworkConnectionRequired }
            .firstOrNull()
        when {
            selected != null -> {
                tts.voice = selected
                Timber.d("使用指定语音 name=%s", selected.name)
            }
            automatic != null -> {
                tts.voice = automatic
                Timber.d("使用自动匹配语音 name=%s locale=%s", automatic.name, automatic.locale)
            }
            else -> {
                tts.language = automaticLocale
                Timber.d("回退到语言 locale=%s", automaticLocale)
            }
        }
    }

    private fun pausePlayback(fromFocusLoss: Boolean) {
        if (manager.state.value.status != TtsPlaybackStatus.PLAYING) return
        Timber.i("暂停朗读 fromFocusLoss=%b", fromFocusLoss)
        resumeAfterFocusGain = fromFocusLoss
        currentUtteranceId = null
        tts.stop()
        if (!fromFocusLoss) audioManager.abandonAudioFocusRequest(audioFocusRequest)
        publish(TtsPlaybackStatus.PAUSED)
    }

    private fun resumePlayback() {
        if (manager.state.value.status != TtsPlaybackStatus.PAUSED) return
        Timber.i("继续朗读")
        resumeAfterFocusGain = false
        speakCurrent()
    }

    private fun stopPlayback(status: TtsPlaybackStatus) {
        Timber.i("停止朗读 status=%s", status)
        playbackJob?.cancel()
        playbackJob = null
        currentUtteranceId = null
        tts.stop()
        audioManager.abandonAudioFocusRequest(audioFocusRequest)
        resumeAfterFocusGain = false
        publish(status, sentence = null)
        foregroundStarted = false
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun handleSynthesisFailure(reason: String, errorCode: Int? = null) {
        val sentence = sentences.getOrNull(sentenceIndex)
        Timber.w(
            "TTS 朗读失败 reason=%s errorCode=%s sentenceIndex=%d 重试=%d/%d 句子=[%s]",
            reason,
            errorCode,
            sentenceIndex,
            currentSentenceRetries,
            MAX_RETRIES_PER_SENTENCE,
            sentence?.text,
        )
        if (currentSentenceRetries < MAX_RETRIES_PER_SENTENCE) {
            currentSentenceRetries++
            Timber.i("重试当前句子 第%d/%d次", currentSentenceRetries, MAX_RETRIES_PER_SENTENCE)
            scope.launch {
                delay(RETRY_DELAY_MS.milliseconds)
                speakCurrentOrAdvance()
            }
            return
        }
        // 重试上限用尽，跳过当前句
        currentSentenceRetries = 0
        consecutiveErrors++
        if (consecutiveErrors >= MAX_CONSECUTIVE_ERRORS) {
            fail("连续 $consecutiveErrors 句朗读失败，TTS 引擎不可用")
            return
        }
        Timber.w("跳过当前句 sentenceIndex=%d", sentenceIndex)
        sentenceIndex++
        scope.launch { speakCurrentOrAdvance() }
    }

    private fun fail(message: String) {
        Timber.w("朗读终止 message=%s", message)
        playbackJob?.cancel()
        playbackJob = null
        currentUtteranceId = null
        tts.stop()
        audioManager.abandonAudioFocusRequest(audioFocusRequest)
        publish(TtsPlaybackStatus.ERROR, error = message, sentence = null)
        foregroundStarted = false
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun onAudioFocusChanged(change: Int) {
        Timber.d("音频焦点变化 change=%d", change)
        when (change) {
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK,
            -> pausePlayback(true)
            AudioManager.AUDIOFOCUS_LOSS -> pausePlayback(false)
            AudioManager.AUDIOFOCUS_GAIN -> if (resumeAfterFocusGain) resumePlayback()
        }
    }

    private fun saveCurrentProgress() {
        val parsedBook = book ?: return
        val chapter = parsedBook.chapters.getOrNull(chapterIndex) ?: return
        val sentence = sentences.getOrNull(sentenceIndex) ?: return
        val chapterContent = content ?: return
        scope.launch {
            container.progressRepository.save(
                checkNotNull(bookId),
                ReaderLocator(chapter.href, chapterIndex, chapterContent.progressAt(sentence)),
            )
        }
    }

    private fun publish(
        status: TtsPlaybackStatus,
        requestedBookId: String? = bookId,
        sentence: TtsSentence? = sentences.getOrNull(sentenceIndex),
        error: String? = null,
    ) {
        val parsedBook = book
        val state = TtsPlaybackState(
            status = status,
            bookId = requestedBookId,
            bookTitle = parsedBook?.book?.title,
            chapterTitle = parsedBook?.chapters?.getOrNull(chapterIndex)?.title,
            activeSentence = sentence,
            error = error,
        )
        manager.publish(state)
        updateMediaSession(state)
        if (foregroundStarted) getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification(state))
    }

    private fun ensureForeground(state: TtsPlaybackState) {
        if (!foregroundStarted) {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                buildNotification(state),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
            )
            foregroundStarted = true
        }
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
        val isPlaying = state.status == TtsPlaybackStatus.PLAYING || state.status == TtsPlaybackStatus.PREPARING
        val toggleAction = if (isPlaying) ACTION_PAUSE else ACTION_RESUME
        val toggleLabel = if (isPlaying) "暂停" else "继续"
        val toggleIcon = if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
        val contentIntent = PendingIntent.getActivity(
            this,
            1,
            Intent(this, MainActivity::class.java).apply {
                action = MainActivity.ACTION_OPEN_TTS_BOOK
                putExtra(MainActivity.EXTRA_TTS_BOOK_ID, state.bookId)
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle(state.bookTitle ?: "正在准备朗读")
            .setContentText(state.activeSentence?.text ?: state.chapterTitle ?: "拾光阅读")
            .setContentIntent(contentIntent)
            .setOngoing(state.status !in setOf(TtsPlaybackStatus.ERROR, TtsPlaybackStatus.COMPLETED))
            .setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_TRANSPORT)
            .addAction(
                Notification.Action.Builder(
                    Icon.createWithResource(this, toggleIcon),
                    toggleLabel,
                    servicePendingIntent(toggleAction, 2),
                ).build(),
            )
            .addAction(
                Notification.Action.Builder(
                    Icon.createWithResource(this, android.R.drawable.ic_menu_close_clear_cancel),
                    "停止",
                    servicePendingIntent(ACTION_STOP, 3, source = "notification"),
                ).build(),
            )
            .setStyle(Notification.MediaStyle().setMediaSession(mediaSession.sessionToken).setShowActionsInCompactView(0, 1))
            .build()
    }

    private fun servicePendingIntent(action: String, requestCode: Int, source: String? = null): PendingIntent = PendingIntent.getService(
        this,
        requestCode,
        Intent(this, TtsPlaybackService::class.java).setAction(action).apply {
            if (source != null) putExtra(EXTRA_SOURCE, source)
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun createNotificationChannel() {
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "朗读播放", NotificationManager.IMPORTANCE_LOW),
        )
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
        private const val CHANNEL_ID = "tts_playback"
        private const val NOTIFICATION_ID = 2001
        private const val MAX_CONSECUTIVE_ERRORS = 8
        private const val MAX_RETRIES_PER_SENTENCE = 5
        private const val RETRY_DELAY_MS = 300L
    }
}
