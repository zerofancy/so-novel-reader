package top.ntutn.sonovelreader.ui

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import java.io.IOException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import top.ntutn.sonovelreader.AppContainer
import top.ntutn.sonovelreader.data.ImportBatchResult
import top.ntutn.sonovelreader.data.ParsedBook
import top.ntutn.sonovelreader.data.ReaderContent
import top.ntutn.sonovelreader.data.ReaderLocator
import top.ntutn.sonovelreader.data.ReaderSettings
import top.ntutn.sonovelreader.data.ReaderTheme
import top.ntutn.sonovelreader.data.ReadingMode
import top.ntutn.sonovelreader.data.ShelfBook
import top.ntutn.sonovelreader.tts.TtsPlaybackStatus
import top.ntutn.sonovelreader.tts.TtsPlaybackState
import top.ntutn.sonovelreader.tts.TtsVoiceCatalogState

data class LibraryUiState(
    val books: List<ShelfBook> = emptyList(),
    val importing: Boolean = false,
    val deletingBookId: String? = null,
)

class LibraryViewModel(private val container: AppContainer) : ViewModel() {
    private val mutableState = MutableStateFlow(LibraryUiState())
    val state: StateFlow<LibraryUiState> = mutableState.asStateFlow()
    private val eventChannel = Channel<LibraryEvent>(Channel.BUFFERED)
    val events = eventChannel.receiveAsFlow()

    init {
        viewModelScope.launch {
            container.bookRepository.observeShelf().collect { books ->
                mutableState.value = mutableState.value.copy(books = books)
            }
        }
    }

    fun importBooks(uris: List<Uri>) {
        if (uris.isEmpty() || mutableState.value.importing) return
        viewModelScope.launch {
            mutableState.value = mutableState.value.copy(importing = true)
            val result = runCatching { container.bookRepository.importBooks(uris) }
                .getOrElse { error ->
                    ImportBatchResult(
                        listOf(
                            top.ntutn.sonovelreader.data.ImportItemResult.Failed(
                                displayName = "共享文件",
                                message = error.message ?: "无法读取共享文件",
                            ),
                        ),
                    )
                }
            mutableState.value = mutableState.value.copy(importing = false)
            eventChannel.send(LibraryEvent.ImportFinished(result))
        }
    }

    fun requestDelete(bookId: String) {
        mutableState.value = mutableState.value.copy(deletingBookId = bookId)
    }

    fun cancelDelete() {
        mutableState.value = mutableState.value.copy(deletingBookId = null)
    }

    fun confirmDelete() {
        val id = mutableState.value.deletingBookId ?: return
        mutableState.value = mutableState.value.copy(deletingBookId = null)
        viewModelScope.launch {
            container.bookRepository.deleteBook(id)
            eventChannel.send(LibraryEvent.Message("书籍已删除"))
        }
    }
}

sealed interface LibraryEvent {
    data class ImportFinished(val result: ImportBatchResult) : LibraryEvent
    data class Message(val text: String) : LibraryEvent
}

class SettingsViewModel(private val container: AppContainer) : ViewModel() {
    val settings = container.settingsRepository.settings.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        ReaderSettings(),
    )
    private val mutableTtsVoices = MutableStateFlow(TtsVoiceCatalogState())
    val ttsVoices = mutableTtsVoices.asStateFlow()

    fun setMode(value: ReadingMode) = viewModelScope.launch { container.settingsRepository.setReadingMode(value) }
    fun setFontSize(value: Int) = viewModelScope.launch { container.settingsRepository.setFontSize(value) }
    fun setLineHeight(value: Float) = viewModelScope.launch { container.settingsRepository.setLineHeight(value) }
    fun setFirstLineIndent(value: Boolean) = viewModelScope.launch { container.settingsRepository.setFirstLineIndent(value) }
    fun setParagraphSpacing(value: Int) = viewModelScope.launch { container.settingsRepository.setParagraphSpacing(value) }
    fun setTheme(value: ReaderTheme) = viewModelScope.launch { container.settingsRepository.setTheme(value) }
    fun setKeepScreenOn(value: Boolean) = viewModelScope.launch { container.settingsRepository.setKeepScreenOn(value) }
    fun setTtsRate(value: Float) = updateTtsSettings(settings.value.copy(ttsRate = value.coerceIn(0.5f, 2f))) {
        container.settingsRepository.setTtsRate(value)
    }
    fun setTtsPitch(value: Float) = updateTtsSettings(settings.value.copy(ttsPitch = value.coerceIn(0.5f, 2f))) {
        container.settingsRepository.setTtsPitch(value)
    }
    fun setTtsVoiceName(value: String?) = updateTtsSettings(settings.value.copy(ttsVoiceName = value)) {
        container.settingsRepository.setTtsVoiceName(value)
    }

    fun loadTtsVoices() {
        if (mutableTtsVoices.value.loading || mutableTtsVoices.value.voices.isNotEmpty()) return
        viewModelScope.launch {
            mutableTtsVoices.value = TtsVoiceCatalogState(loading = true)
            mutableTtsVoices.value = container.ttsVoiceCatalog.load()
        }
    }

    private fun updateTtsSettings(value: ReaderSettings, update: suspend () -> Unit) = viewModelScope.launch {
        update()
        if (container.ttsPlaybackManager.state.value.status in setOf(TtsPlaybackStatus.PLAYING, TtsPlaybackStatus.PAUSED)) {
            container.ttsPlaybackManager.updateSettings(value)
        }
    }
}

data class ReaderUiState(
    val loading: Boolean = true,
    val parsedBook: ParsedBook? = null,
    val locator: ReaderLocator? = null,
    val content: ReaderContent? = null,
    val contentLoading: Boolean = false,
    val settings: ReaderSettings = ReaderSettings(),
    val error: String? = null,
    val ttsPlayback: TtsPlaybackState = TtsPlaybackState(),
)

class ReaderViewModel(
    private val bookId: String,
    private val container: AppContainer,
) : ViewModel() {
    private val mutableState = MutableStateFlow(ReaderUiState())
    val state = mutableState.asStateFlow()
    private var saveJob: Job? = null
    private var loadJob: Job? = null
    private var chapterJob: Job? = null
    private val chapterCache = object : LinkedHashMap<String, ReaderContent>(CHAPTER_CACHE_SIZE, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ReaderContent>?): Boolean =
            size > CHAPTER_CACHE_SIZE
    }

    init {
        viewModelScope.launch {
            container.settingsRepository.settings.collect { settings ->
                mutableState.value = mutableState.value.copy(settings = settings)
            }
        }
        viewModelScope.launch {
            container.ttsPlaybackManager.state.collect { playback ->
                mutableState.value = mutableState.value.copy(ttsPlayback = playback)
            }
        }
        load()
    }

    fun load() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            mutableState.value = mutableState.value.copy(loading = true, error = null)
            try {
                val book = container.bookRepository.openBook(bookId)
                val saved = container.progressRepository.get(bookId)
                val index = saved?.chapterHref?.let { href -> book.chapters.indexOfFirst { it.href == href } }
                    ?.takeIf { it >= 0 }
                    ?: saved?.chapterIndex?.coerceIn(0, book.chapters.lastIndex)
                    ?: 0
                val locator = ReaderLocator(
                    chapterHref = book.chapters[index].href,
                    chapterIndex = index,
                    chapterFraction = saved?.chapterFraction?.coerceIn(0f, 1f) ?: 0f,
                )
                mutableState.value = mutableState.value.copy(
                    loading = false,
                    parsedBook = book,
                    locator = locator,
                    content = null,
                )
                loadChapter(book, index)
            } catch (error: Exception) {
                mutableState.value = mutableState.value.copy(
                    loading = false,
                    error = error.message ?: "无法打开这本书",
                )
            }
        }
    }

    fun goToChapter(index: Int, fraction: Float = 0f) {
        val book = mutableState.value.parsedBook ?: return
        if (index !in book.chapters.indices) return
        updateLocator(ReaderLocator(book.chapters[index].href, index, fraction), true)
        loadChapter(book, index)
    }

    fun updateFraction(fraction: Float) {
        val current = mutableState.value.locator ?: return
        updateLocator(current.copy(chapterFraction = fraction.coerceIn(0f, 1f)), false)
    }

    fun flushProgress() {
        mutableState.value.locator?.let { locator ->
            saveJob?.cancel()
            viewModelScope.launch { container.progressRepository.save(bookId, locator) }
        }
    }

    fun setMode(value: ReadingMode) = viewModelScope.launch { container.settingsRepository.setReadingMode(value) }

    fun toggleTts() {
        val state = mutableState.value
        val playback = state.ttsPlayback
        when {
            playback.bookId == bookId && playback.status in setOf(TtsPlaybackStatus.PLAYING, TtsPlaybackStatus.PREPARING) ->
                container.ttsPlaybackManager.pause()
            playback.bookId == bookId && playback.status == TtsPlaybackStatus.PAUSED ->
                container.ttsPlaybackManager.resume()
            else -> state.locator?.let { container.ttsPlaybackManager.play(bookId, it, state.settings) }
        }
    }

    fun stopTtsForManualNavigation() {
        val playback = mutableState.value.ttsPlayback
        if (playback.bookId == bookId && playback.status in setOf(
                TtsPlaybackStatus.PREPARING,
                TtsPlaybackStatus.PLAYING,
                TtsPlaybackStatus.PAUSED,
            )
        ) {
            container.ttsPlaybackManager.stop()
        }
    }

    private fun loadChapter(book: ParsedBook, index: Int) {
        val chapter = book.chapters[index]
        chapterJob?.cancel()
        chapterCache[chapter.href]?.let { cached ->
            mutableState.value = mutableState.value.copy(content = cached, contentLoading = false, error = null)
            return
        }
        mutableState.value = mutableState.value.copy(content = null, contentLoading = true, error = null)
        chapterJob = viewModelScope.launch {
            try {
                val content = container.bookRepository.readChapter(book, chapter)
                chapterCache[chapter.href] = content
                if (mutableState.value.locator?.chapterHref == chapter.href) {
                    mutableState.value = mutableState.value.copy(content = content, contentLoading = false)
                }
            } catch (error: Exception) {
                if (mutableState.value.locator?.chapterHref == chapter.href) {
                    mutableState.value = mutableState.value.copy(
                        contentLoading = false,
                        error = error.message ?: "无法解析本章内容",
                    )
                }
            }
        }
    }

    private fun updateLocator(locator: ReaderLocator, immediate: Boolean) {
        mutableState.value = mutableState.value.copy(locator = locator)
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            if (!immediate) delay(500)
            container.progressRepository.save(bookId, locator)
        }
    }

    private companion object {
        const val CHAPTER_CACHE_SIZE = 8
    }
}

class AppViewModelFactory(
    private val container: AppContainer,
    private val bookId: String? = null,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = when {
        modelClass.isAssignableFrom(LibraryViewModel::class.java) -> LibraryViewModel(container) as T
        modelClass.isAssignableFrom(SettingsViewModel::class.java) -> SettingsViewModel(container) as T
        modelClass.isAssignableFrom(ReaderViewModel::class.java) && bookId != null -> ReaderViewModel(bookId, container) as T
        else -> throw IllegalArgumentException("未知 ViewModel：${modelClass.name}")
    }
}
