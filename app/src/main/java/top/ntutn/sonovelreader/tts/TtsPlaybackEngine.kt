package top.ntutn.sonovelreader.tts

import android.content.Context
import android.graphics.Bitmap
import android.media.AudioManager
import android.media.MediaPlayer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.io.File
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
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import kotlin.time.Duration.Companion.milliseconds
import top.ntutn.sonovelreader.AppContainer
import top.ntutn.sonovelreader.data.ParsedBook
import top.ntutn.sonovelreader.data.ReaderContent
import top.ntutn.sonovelreader.data.ReaderLocator
import top.ntutn.sonovelreader.data.ReaderSettings
import top.ntutn.sonovelreader.ui.loadBookCoverBitmap

/**
 * 朗读核心：用 [TextToSpeech.synthesizeToFile] 预生成后续句子缓存，
 * 用 [MediaPlayer] 播放，实现"合成在后台、播放零间隙、重试不打断听感"。
 */
class TtsPlaybackEngine(
    private val context: Context,
    private val container: AppContainer,
    private val audioFocus: AudioFocusController,
    private val onState: (TtsPlaybackState) -> Unit,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val ttsReady = CompletableDeferred<Int>()

    private var status = TtsPlaybackStatus.IDLE
    private var bookId: String? = null
    private var book: ParsedBook? = null
    private var coverBitmap: Bitmap? = null
    private var content: ReaderContent? = null
    private var chapterIndex = 0
    private var sentences: List<TtsSentence> = emptyList()
    private var sentenceIndex = 0
    private var settings = ReaderSettings()
    private var resumeAfterFocusGain = false

    private val audioBuffer = LinkedHashMap<Int, File>()
    private val failedSentences = mutableSetOf<Int>()
    private var prefetchJob: Job? = null
    private var playbackJob: Job? = null
    private var mediaPlayer: MediaPlayer? = null
    private var completionSignal: CompletableDeferred<Unit>? = null
    private var pendingSynthesis: Pair<String, CompletableDeferred<Boolean>>? = null
    private var consecutiveErrors = 0

    private val tts: TextToSpeech = TextToSpeech(context) { state ->
        Timber.i("TTS 引擎初始化 status=%d", state)
        ttsReady.complete(state)
    }.apply {
        setAudioAttributes(audioFocus.attributes)
        setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                // 合成到文件时不依赖 onStart
            }

            override fun onDone(utteranceId: String?) {
                scope.launch {
                    pendingSynthesis?.let { (id, deferred) ->
                        if (id == utteranceId) {
                            Timber.d("合成完成 utteranceId=%s", utteranceId)
                            deferred.complete(true)
                        }
                    }
                }
            }

            @Deprecated("Deprecated by Android")
            override fun onError(utteranceId: String?) = onError(utteranceId, -1)

            override fun onError(utteranceId: String?, errorCode: Int) {
                scope.launch {
                    pendingSynthesis?.let { (id, deferred) ->
                        if (id == utteranceId) {
                            Timber.w("合成失败 utteranceId=%s errorCode=%d", utteranceId, errorCode)
                            deferred.complete(false)
                        }
                    }
                }
            }
        })
    }

    fun start(bookId: String, chapter: Int, fraction: Float) {
        playbackJob?.cancel()
        playbackJob = scope.launch { startPlayback(bookId, chapter, fraction) }
    }

    fun pause(fromFocusLoss: Boolean) {
        if (status != TtsPlaybackStatus.PLAYING) return
        Timber.i("暂停朗读 fromFocusLoss=%b", fromFocusLoss)
        resumeAfterFocusGain = fromFocusLoss
        mediaPlayer?.pause()
        if (!fromFocusLoss) audioFocus.abandon()
        emit(TtsPlaybackStatus.PAUSED)
    }

    fun resume() {
        if (status != TtsPlaybackStatus.PAUSED) return
        if (mediaPlayer == null) return // 等待播放驱动恢复（如音频焦点抢占中）
        if (!audioFocus.request()) {
            emit(TtsPlaybackStatus.PAUSED, error = "其他应用正在使用音频")
            return
        }
        Timber.i("继续朗读")
        resumeAfterFocusGain = false
        mediaPlayer?.start()
        emit(TtsPlaybackStatus.PLAYING)
    }

    fun stop() = stop(TtsPlaybackStatus.IDLE)

    fun stop(stopStatus: TtsPlaybackStatus) {
        Timber.i("停止朗读 status=%s", stopStatus)
        cleanup()
        emit(stopStatus, sentence = null)
    }

    fun onAudioFocusChange(change: Int) {
        Timber.d("音频焦点变化 change=%d", change)
        when (change) {
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK,
            -> pause(true)
            AudioManager.AUDIOFOCUS_LOSS -> pause(false)
            AudioManager.AUDIOFOCUS_GAIN -> if (resumeAfterFocusGain) resume()
        }
    }

    fun updateSettings(newSettings: ReaderSettings) {
        settings = newSettings
        // 清除已缓存但未播放的句子，以便用新设置重新合成
        audioBuffer.keys.filter { it > sentenceIndex }.toList().forEach { idx ->
            audioBuffer.remove(idx)?.delete()
        }
        failedSentences.clear()
        startPrefetch()
        if (status == TtsPlaybackStatus.PAUSED) emit(TtsPlaybackStatus.PAUSED)
    }

    fun release() {
        cleanup()
        runCatching { tts.stop() }
        runCatching { tts.shutdown() }
        scope.cancel()
    }

    private fun cleanup() {
        playbackJob?.cancel(); playbackJob = null
        prefetchJob?.cancel(); prefetchJob = null
        pendingSynthesis = null
        completionSignal?.complete(Unit)
        mediaPlayer?.release(); mediaPlayer = null
        runCatching { tts.stop() }
        audioFocus.abandon()
        resumeAfterFocusGain = false
        coverBitmap = null
        clearAllCache()
    }

    private suspend fun startPlayback(requestedBookId: String, requestedChapter: Int, requestedFraction: Float) {
        clearAllCache()
        runCatching { tts.stop() }
        pendingSynthesis = null
        consecutiveErrors = 0
        bookId = requestedBookId
        book = null
        coverBitmap = null
        content = null
        sentences = emptyList()
        sentenceIndex = 0
        Timber.i("开始朗读 bookId=%s chapter=%d fraction=%f", requestedBookId, requestedChapter, requestedFraction)
        emit(TtsPlaybackStatus.PREPARING, sentence = null)
        if (ttsReady.await() != TextToSpeech.SUCCESS) {
            Timber.w("TTS 未就绪，初始化失败")
            fail("系统 TTS 初始化失败，请检查语音引擎")
            return
        }
        try {
            val parsedBook = container.bookRepository.openBook(requestedBookId)
            book = parsedBook
            coverBitmap = loadBookCoverBitmap(parsedBook.book.coverPath, parsedBook.book.title)
            settings = container.settingsRepository.settings.first()
            chapterIndex = requestedChapter.coerceIn(parsedBook.chapters.indices)
            loadChapter(chapterIndex)
            sentenceIndex = sentenceIndexForProgress(content!!, sentences, requestedFraction).coerceAtLeast(0)
            startPrefetch()
            startPlaybackDriver()
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

    // 播放驱动：逐句消费缓存，跨章节边界加载下一章
    private fun startPlaybackDriver() {
        playbackJob?.cancel()
        playbackJob = scope.launch {
            while (isActive) {
                val parsedBook = book ?: return@launch
                while (sentenceIndex in failedSentences && sentenceIndex < sentences.size) sentenceIndex++
                when (ttsQueueBoundary(chapterIndex, parsedBook.chapters.lastIndex, sentenceIndex, sentences.size)) {
                    TtsQueueBoundary.SPEAK -> {
                        awaitReadyOrFailed(sentenceIndex)
                        if (sentenceIndex in failedSentences) continue
                        val file = audioBuffer[sentenceIndex]
                        val sentence = sentences.getOrNull(sentenceIndex)
                        if (file == null || sentence == null) continue
                        playSentence(file, sentence)
                        cleanupPlayed(sentenceIndex)
                        delay(SENTENCE_GAP_MS.milliseconds) // 句间静音间隙
                        sentenceIndex++
                    }
                    TtsQueueBoundary.NEXT_CHAPTER -> {
                        prefetchJob?.cancel(); prefetchJob = null
                        loadChapter(chapterIndex + 1)
                        clearChapterCache()
                        startPrefetch()
                    }
                    TtsQueueBoundary.COMPLETE -> {
                        val lastChapter = parsedBook.chapters.last()
                        container.progressRepository.save(
                            checkNotNull(bookId),
                            ReaderLocator(lastChapter.href, parsedBook.chapters.lastIndex, 1f),
                        )
                        stop(TtsPlaybackStatus.COMPLETED)
                        return@launch
                    }
                }
            }
        }
    }

    private suspend fun CoroutineScope.awaitReadyOrFailed(index: Int) {
        while (isActive && index !in audioBuffer && index !in failedSentences) {
            delay(AWAIT_POLL_MS.milliseconds)
        }
    }

    private suspend fun CoroutineScope.playSentence(file: File, sentence: TtsSentence) {
        while (isActive && !audioFocus.request()) {
            emit(TtsPlaybackStatus.PAUSED, error = "其他应用正在使用音频", sentence = sentence)
            delay(FOCUS_POLL_MS.milliseconds)
        }
        if (!isActive) return
        resumeAfterFocusGain = false
        val signal = CompletableDeferred<Unit>()
        completionSignal = signal
        mediaPlayer?.release()
        mediaPlayer = MediaPlayer().apply {
            setAudioAttributes(audioFocus.attributes)
            setOnCompletionListener { signal.complete(Unit) }
            setOnErrorListener { _, _, _ ->
                Timber.w("MediaPlayer 播放出错")
                signal.complete(Unit)
                true
            }
            try {
                setDataSource(file.absolutePath)
                prepare()
                start()
            } catch (e: Exception) {
                Timber.e(e, "MediaPlayer 准备失败")
                signal.complete(Unit)
            }
        }
        emit(TtsPlaybackStatus.PLAYING, sentence = sentence)
        saveCurrentProgress()
        try {
            signal.await()
        } finally {
            completionSignal = null
            mediaPlayer?.release()
            mediaPlayer = null
        }
    }

    // 前瞻合成：保持当前播放位置之后 PREFETCH_COUNT 句的就绪缓存
    private fun startPrefetch() {
        prefetchJob?.cancel()
        prefetchJob = scope.launch {
            while (isActive) {
                val target = nextPrefetchTarget()
                if (target == null) {
                    delay(PREFETCH_IDLE_MS.milliseconds)
                    continue
                }
                if (!synthesizeSentence(target)) continue
            }
        }
    }

    private fun nextPrefetchTarget(): Int? {
        if (sentences.isEmpty()) return null
        val maxIndex = (sentenceIndex + PREFETCH_COUNT).coerceIn(0, sentences.lastIndex)
        for (i in sentenceIndex..maxIndex) {
            if (i !in audioBuffer && i !in failedSentences) return i
        }
        return null
    }

    private suspend fun CoroutineScope.synthesizeSentence(index: Int): Boolean {
        val sentence = sentences.getOrNull(index) ?: return false
        val file = audioFileFor(chapterIndex, index)
        file.delete()
        var retries = 0
        while (retries <= MAX_RETRIES_PER_SENTENCE && isActive) {
            runCatching { tts.stop() } // 清除可能残留的合成请求，避免重试排在卡住的请求之后
            configureVoice(sentence.text)
            tts.setSpeechRate(settings.ttsRate.coerceIn(0.5f, 2f))
            tts.setPitch(settings.ttsPitch.coerceIn(0.5f, 2f))
            val id = UUID.randomUUID().toString()
            val deferred = CompletableDeferred<Boolean>()
            pendingSynthesis = id to deferred
            val code = tts.synthesizeToFile(sentence.text, null, file, id)
            if (code == TextToSpeech.ERROR) {
                pendingSynthesis = null
                Timber.w("synthesizeToFile 同步返回 ERROR index=%d 重试=%d", index, retries)
            } else {
                val ok = withTimeoutOrNull(SYNTHESIS_TIMEOUT_MS.milliseconds) { deferred.await() }
                pendingSynthesis = null
                if (ok == true && file.exists() && file.length() > 0) {
                    audioBuffer[index] = file
                    consecutiveErrors = 0
                    Timber.d("缓存就绪 index=%d", index)
                    return true
                }
                Timber.w("合成失败或超时 index=%d 重试=%d", index, retries)
            }
            retries++
            if (retries <= MAX_RETRIES_PER_SENTENCE) delay(RETRY_DELAY_MS.milliseconds)
        }
        // 重试上限用尽，标记跳过
        failedSentences.add(index)
        consecutiveErrors++
        Timber.w("跳过当前句 index=%d 连续失败=%d", index, consecutiveErrors)
        file.delete()
        if (consecutiveErrors >= MAX_CONSECUTIVE_ERRORS) {
            fail("连续 $consecutiveErrors 句朗读失败，TTS 引擎不可用")
        }
        return false
    }

    private fun audioFileFor(chapter: Int, index: Int): File {
        val safeBookId = bookId?.filter { it.isLetterOrDigit() } ?: "book"
        return File(context.cacheDir, "tts_${safeBookId}_${chapter}_$index.wav")
    }

    private fun cleanupPlayed(index: Int) {
        audioBuffer.remove(index)?.delete()
        failedSentences.remove(index)
    }

    private fun clearChapterCache() {
        audioBuffer.values.forEach { it.delete() }
        audioBuffer.clear()
        failedSentences.clear()
    }

    private fun clearAllCache() {
        context.cacheDir.listFiles { f -> f.name.startsWith("tts_") && f.name.endsWith(".wav") }
            ?.forEach { it.delete() }
        audioBuffer.clear()
        failedSentences.clear()
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

    private fun fail(message: String) {
        Timber.w("朗读终止 message=%s", message)
        cleanup()
        emit(TtsPlaybackStatus.ERROR, sentence = null, error = message)
    }

    private fun emit(
        status: TtsPlaybackStatus,
        sentence: TtsSentence? = sentences.getOrNull(sentenceIndex),
        error: String? = null,
    ) {
        this.status = status
        val parsedBook = book
        onState(
            TtsPlaybackState(
                status = status,
                bookId = bookId,
                bookTitle = parsedBook?.book?.title,
                chapterTitle = parsedBook?.chapters?.getOrNull(chapterIndex)?.title,
                activeSentence = sentence,
                error = error,
                coverBitmap = coverBitmap,
            ),
        )
    }

    private companion object {
        const val MAX_CONSECUTIVE_ERRORS = 8
        const val MAX_RETRIES_PER_SENTENCE = 5
        const val RETRY_DELAY_MS = 300L
        const val SENTENCE_GAP_MS = 350L
        const val PREFETCH_COUNT = 2
        const val PREFETCH_IDLE_MS = 50L
        const val AWAIT_POLL_MS = 50L
        const val FOCUS_POLL_MS = 500L
        const val SYNTHESIS_TIMEOUT_MS = 10_000L
    }
}
