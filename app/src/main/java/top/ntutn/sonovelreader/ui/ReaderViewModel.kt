package top.ntutn.sonovelreader.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import top.ntutn.sonovelreader.AppContainer
import top.ntutn.sonovelreader.data.local.BookmarkEntity
import top.ntutn.sonovelreader.data.ParsedBook
import top.ntutn.sonovelreader.data.ReaderContent
import top.ntutn.sonovelreader.data.ReaderLocator
import top.ntutn.sonovelreader.data.ReaderSettings
import top.ntutn.sonovelreader.data.ReadingMode
import top.ntutn.sonovelreader.tts.TtsPlaybackStatus
import top.ntutn.sonovelreader.tts.TtsPlaybackState

data class ReaderUiState(
    val bookmarks: List<BookmarkEntity> = emptyList(),
    val bookmarksLoading: Boolean = true,
    val bookmarksError: String? = null,
    val bookmarkJumpToken: Int = 0,
    val loading: Boolean = true,
    val parsedBook: ParsedBook? = null,
    val locator: ReaderLocator? = null,
    val content: ReaderContent? = null,
    val previousChapter: ReaderChapterContent? = null,
    val nextChapter: ReaderChapterContent? = null,
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
    private var bookmarksJob: Job? = null
    private var restartTtsAtLocator = false

    fun observeBookmarks() {
        bookmarksJob?.cancel()
        bookmarksJob = viewModelScope.launch {
            mutableState.update { it.copy(bookmarksLoading = true, bookmarksError = null) }
            try {
                container.bookmarkRepository.observe(bookId).collect { list ->
                    mutableState.update { it.copy(bookmarks = list, bookmarksLoading = false) }
                }
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (_: Exception) {
                mutableState.update { it.copy(bookmarksLoading = false, bookmarksError = "无法读取书签") }
            }
        }
    }

    suspend fun saveBookmark(name: String, locator: ReaderLocator, title: String) =
        container.bookmarkRepository.add(bookId, name, locator, title)

    suspend fun renameBookmark(id: String, name: String) = container.bookmarkRepository.rename(bookId, id, name)
    suspend fun deleteBookmark(id: String) = container.bookmarkRepository.delete(bookId, id)

    fun jumpToBookmark(bookmark: BookmarkEntity, onResult: (String?) -> Unit) {
        val book = mutableState.value.parsedBook ?: return
        if (bookmark.bookId != bookId) return
        val index = book.chapters.indexOfFirst { it.href == bookmark.chapterHref }
        if (index < 0) { onResult("书签位置已失效"); return }
        if (mutableState.value.ttsPlayback.bookId == bookId &&
            mutableState.value.ttsPlayback.status in setOf(TtsPlaybackStatus.PLAYING, TtsPlaybackStatus.PREPARING, TtsPlaybackStatus.PAUSED)
        ) {
            if (mutableState.value.ttsPlayback.status == TtsPlaybackStatus.PREPARING) {
                container.ttsPlaybackManager.stop()
            } else {
                container.ttsPlaybackManager.pause()
            }
            restartTtsAtLocator = true
        }
        chapterJob?.cancel()
        saveJob?.cancel()
        mutableState.update { it.copy(contentLoading = true) }
        chapterJob = viewModelScope.launch {
            try {
                val content = readCachedChapter(book, index)
                val previous = readAdjacentChapter(book, index - 1)
                val next = readAdjacentChapter(book, index + 1)
                val locator = ReaderLocator(bookmark.chapterHref, index, bookmark.chapterFraction.coerceIn(0f, 1f))
                mutableState.update { it.copy(content = content, locator = locator,
                    previousChapter = previous, nextChapter = next, contentLoading = false,
                    bookmarkJumpToken = it.bookmarkJumpToken + 1) }
                updateLocator(locator, true)
                onResult(null)
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (_: Exception) {
                mutableState.update { it.copy(contentLoading = false) }
                onResult("无法打开书签位置，请重试")
            }
        }
    }

    private val chapterCache = object : LinkedHashMap<String, ReaderContent>(CHAPTER_CACHE_SIZE, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ReaderContent>?): Boolean =
            size > CHAPTER_CACHE_SIZE
    }

    init {
        viewModelScope.launch {
            container.settingsRepository.settings.collect { settings ->
                mutableState.update { it.copy(settings = settings) }
            }
        }
        viewModelScope.launch {
            container.ttsPlaybackManager.state.collect { playback ->
                mutableState.update { it.copy(ttsPlayback = playback) }
            }
        }
        observeBookmarks()
        load()
    }

    fun load() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            mutableState.update { it.copy(loading = true, error = null) }
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
                mutableState.update {
                    it.copy(
                        loading = false,
                        parsedBook = book,
                        locator = locator,
                        content = null,
                    )
                }
                loadChapter(book, index)
            } catch (error: Exception) {
                mutableState.update {
                    it.copy(
                        loading = false,
                        error = error.message ?: "无法打开这本书",
                    )
                }
            }
        }
    }

    fun goToChapter(index: Int, fraction: Float = 0f, preservePage: Boolean = false) {
        val book = mutableState.value.parsedBook ?: return
        if (index !in book.chapters.indices) return
        if (preservePage && mutableState.value.contentLoading) return
        loadChapter(book, index, fraction, preservePage)
    }

    fun updateReaderFraction(fraction: Float, chapterHref: String, bookmarkJumpToken: Int) {
        val current = mutableState.value
        // Ignore callbacks from a page that was replaced by a completed jump.
        if (current.locator?.chapterHref != chapterHref || current.bookmarkJumpToken != bookmarkJumpToken) return
        updateFraction(fraction)
    }

    fun updateFraction(fraction: Float) {
        if (mutableState.value.contentLoading) return
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
        val st = mutableState.value
        val playback = st.ttsPlayback
        when {
            restartTtsAtLocator -> st.locator?.let {
                restartTtsAtLocator = false
                container.ttsPlaybackManager.play(bookId, it, st.settings)
            }
            playback.bookId == bookId && playback.status in setOf(TtsPlaybackStatus.PLAYING, TtsPlaybackStatus.PREPARING) ->
                container.ttsPlaybackManager.pause()
            playback.bookId == bookId && playback.status == TtsPlaybackStatus.PAUSED ->
                container.ttsPlaybackManager.resume()
            else -> st.locator?.let { container.ttsPlaybackManager.play(bookId, it, st.settings) }
        }
    }

    private suspend fun readCachedChapter(book: ParsedBook, index: Int): ReaderContent {
        val chapter = book.chapters[index]
        return chapterCache[chapter.href] ?: container.bookRepository.readChapter(book, chapter).also {
            chapterCache[chapter.href] = it
        }
    }

    private suspend fun readAdjacentChapter(book: ParsedBook, index: Int): ReaderChapterContent? {
        val chapter = book.chapters.getOrNull(index) ?: return null
        return try {
            ReaderChapterContent(chapter.title, readCachedChapter(book, index))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // A damaged neighboring chapter must not prevent reading the current one.
            null
        }
    }

    private fun loadChapter(book: ParsedBook, index: Int, fraction: Float? = null, preservePage: Boolean = false) {
        val chapter = book.chapters[index]
        chapterJob?.cancel()
        // Keep the visible page until the new chapter and its boundary previews are ready.
        mutableState.update { it.copy(content = it.content.takeIf { preservePage }, contentLoading = true, error = null) }
        chapterJob = viewModelScope.launch {
            try {
                val content = readCachedChapter(book, index)
                val previous = readAdjacentChapter(book, index - 1)
                val next = readAdjacentChapter(book, index + 1)
                val locator = ReaderLocator(
                    chapter.href, index,
                    fraction ?: mutableState.value.locator?.chapterFraction ?: 0f,
                )
                mutableState.update {
                    it.copy(
                        locator = locator,
                        content = content,
                        previousChapter = previous,
                        nextChapter = next,
                        contentLoading = false,
                    )
                }
                updateLocator(locator, fraction != null)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                mutableState.update {
                    it.copy(contentLoading = false, error = error.message ?: "无法解析本章内容")
                }
            }
        }
    }

    private fun updateLocator(locator: ReaderLocator, immediate: Boolean) {
        mutableState.update { it.copy(locator = locator) }
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
