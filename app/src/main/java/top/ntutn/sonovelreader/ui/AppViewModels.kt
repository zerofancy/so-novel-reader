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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import top.ntutn.sonovelreader.AppContainer
import top.ntutn.sonovelreader.data.EditGroupState
import top.ntutn.sonovelreader.data.GroupShelfUiState
import top.ntutn.sonovelreader.data.ImportBatchResult
import top.ntutn.sonovelreader.data.MoveBookState
import top.ntutn.sonovelreader.data.ParsedBook
import top.ntutn.sonovelreader.data.ReaderContent
import top.ntutn.sonovelreader.data.ReaderLocator
import top.ntutn.sonovelreader.data.ReaderSettings
import top.ntutn.sonovelreader.data.ReaderTheme
import top.ntutn.sonovelreader.data.ReadingMode
import top.ntutn.sonovelreader.data.ShelfBook
import top.ntutn.sonovelreader.data.ShelfGroup
import top.ntutn.sonovelreader.tts.TtsPlaybackStatus
import top.ntutn.sonovelreader.tts.TtsPlaybackState
import top.ntutn.sonovelreader.tts.TtsVoiceCatalogState

data class LibraryUiState(
    val groups: List<ShelfGroup> = emptyList(),
    val ungroupedBooks: List<ShelfBook> = emptyList(),
    val importing: Boolean = false,
    val deletingBookId: String? = null,
    val deletingGroupId: String? = null,
    val editingGroup: EditGroupState? = null,
    val movingBook: MoveBookState? = null,
    /** 当从「移动书籍」对话框中点击「新建分组」时，记录待移动的书籍 ID；分组创建完成后自动把书移入。 */
    val pendingMoveBookId: String? = null,
)

class LibraryViewModel(private val container: AppContainer) : ViewModel() {
    private val mutableState = MutableStateFlow(LibraryUiState())
    val state: StateFlow<LibraryUiState> = mutableState.asStateFlow()
    private val eventChannel = Channel<LibraryEvent>(Channel.BUFFERED)
    val events = eventChannel.receiveAsFlow()

    init {
        viewModelScope.launch {
            container.bookRepository.observeTopLevelShelf()
                .collect { (groups, books) ->
                    mutableState.update { it.copy(groups = groups, ungroupedBooks = books) }
                }
        }
    }

    // ===== 导入 EPUB =====

    fun importBooks(uris: List<Uri>) {
        if (uris.isEmpty() || mutableState.value.importing) return
        viewModelScope.launch {
            mutableState.update { it.copy(importing = true) }
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
            mutableState.update { it.copy(importing = false) }
            eventChannel.send(LibraryEvent.ImportFinished(result))
        }
    }

    // ===== 删除书籍 =====

    fun requestDelete(bookId: String) {
        mutableState.update { it.copy(deletingBookId = bookId) }
    }

    fun cancelDelete() {
        mutableState.update { it.copy(deletingBookId = null) }
    }

    fun confirmDelete() {
        val id = mutableState.value.deletingBookId ?: return
        mutableState.update { it.copy(deletingBookId = null) }
        viewModelScope.launch {
            container.bookRepository.deleteBook(id)
            eventChannel.send(LibraryEvent.Message("书籍已删除"))
        }
    }

    // ===== 分组：新建 / 重命名 =====

    fun showCreateGroupDialog() =
        mutableState.update { it.copy(editingGroup = EditGroupState(null, ""), pendingMoveBookId = null) }

    fun showRenameGroupDialog(groupId: String) = viewModelScope.launch {
        val name = container.bookRepository.getGroups()
            .find { it.id == groupId }?.name.orEmpty()
        mutableState.update { it.copy(editingGroup = EditGroupState(groupId, name)) }
    }

    fun dismissEditGroupDialog() =
        mutableState.update { it.copy(editingGroup = null, pendingMoveBookId = null) }

    /**
     * 从「移动书籍」对话框中点击「新建分组」时调用：
     * 记住待移动的书，关闭移动对话框，打开新建分组对话框。
     */
    fun requestMoveToNewGroup(bookId: String) {
        mutableState.update {
            it.copy(movingBook = null, editingGroup = EditGroupState(null, ""), pendingMoveBookId = bookId)
        }
    }

    fun confirmEditGroup(name: String) {
        val st = mutableState.value.editingGroup ?: return
        val trimmed = name.trim()
        if (trimmed.isEmpty()) {
            viewModelScope.launch { eventChannel.send(LibraryEvent.Message("分组名不能为空")) }
            return
        }
        viewModelScope.launch {
            runCatching {
                if (st.editingId == null) {
                    val newGroupId = container.bookRepository.createGroup(trimmed)
                    // 如果是从「移动书籍」对话框跳过来的，自动把书移入新分组
                    val pendingBookId = mutableState.value.pendingMoveBookId
                    if (pendingBookId != null) {
                        container.bookRepository.moveBookToGroup(pendingBookId, newGroupId)
                    }
                    mutableState.update { it.copy(editingGroup = null, pendingMoveBookId = null) }
                    if (pendingBookId != null) {
                        eventChannel.send(LibraryEvent.Message("已创建分组并移入"))
                    }
                } else {
                    container.bookRepository.renameGroup(st.editingId, trimmed)
                    mutableState.update { it.copy(editingGroup = null) }
                }
            }.onFailure {
                eventChannel.send(LibraryEvent.Message("分组名已存在"))
            }
        }
    }

    // ===== 分组：删除 =====

    fun requestDeleteGroup(groupId: String) =
        mutableState.update { it.copy(deletingGroupId = groupId) }

    fun cancelDeleteGroup() = mutableState.update { it.copy(deletingGroupId = null) }

    fun confirmDeleteGroup() = viewModelScope.launch {
        val id = mutableState.value.deletingGroupId ?: return@launch
        val bookCount = mutableState.value.groups.find { it.id == id }?.bookCount ?: 0
        container.bookRepository.deleteGroup(id)
        mutableState.update { it.copy(deletingGroupId = null) }
        eventChannel.send(
            LibraryEvent.Message(
                if (bookCount > 0) "分组已删除，组内 ${bookCount} 本书回到书架顶层"
                else "分组已删除"
            )
        )
    }

    // ===== 移动书籍到分组 =====

    fun requestMoveBook(bookId: String, bookTitle: String, currentGroupId: String?) =
        mutableState.update {
            it.copy(movingBook = MoveBookState(bookId, bookTitle, currentGroupId))
        }

    fun cancelMoveBook() = mutableState.update { it.copy(movingBook = null) }

    fun confirmMoveBook(targetGroupId: String?) = viewModelScope.launch {
        val mb = mutableState.value.movingBook ?: return@launch
        container.bookRepository.moveBookToGroup(mb.bookId, targetGroupId)
        mutableState.update { it.copy(movingBook = null) }
        eventChannel.send(
            LibraryEvent.Message(
                if (targetGroupId == null) "已移回书架顶层"
                else "已移入分组"
            )
        )
    }
}

sealed interface LibraryEvent {
    data class ImportFinished(val result: ImportBatchResult) : LibraryEvent
    data class Message(val text: String) : LibraryEvent
    data object PopBackStack : LibraryEvent
}

// =====================================
// 分组详情 ViewModel
// =====================================

class GroupShelfViewModel(
    private val groupId: String,
    private val groupNameInit: String,
    private val container: AppContainer,
) : ViewModel() {
    private val mutableState = MutableStateFlow(
        GroupShelfUiState(groupId = groupId, groupName = groupNameInit)
    )
    val state: StateFlow<GroupShelfUiState> = mutableState.asStateFlow()

    private val eventChannel = Channel<LibraryEvent>(Channel.BUFFERED)
    val events = eventChannel.receiveAsFlow()

    init {
        viewModelScope.launch {
            container.bookRepository.observeGroupShelf(groupId).collect { books ->
                mutableState.update { it.copy(books = books) }
            }
        }
        viewModelScope.launch {
            container.bookRepository.observeGroups().collect { groups ->
                // 如果分组被重命名，也要同步更新 groupName
                val current = groups.find { it.id == groupId }
                if (current == null) {
                    // 分组被删掉了，通知上层回退
                    eventChannel.send(LibraryEvent.PopBackStack)
                } else {
                    mutableState.update {
                        if (it.groupName != current.name) it.copy(groupName = current.name)
                        else it
                    }
                }
            }
        }
    }

    // ===== 管理分组（从详情页顶栏也能重命名/删除）=====

    suspend fun getCurrentGroupName(): String? =
        container.bookRepository.getGroups().find { it.id == groupId }?.name

    fun requestDelete(bookId: String) = mutableState.update { it.copy(deletingBookId = bookId) }
    fun cancelDelete() = mutableState.update { it.copy(deletingBookId = null) }
    fun confirmDelete() = viewModelScope.launch {
        val id = mutableState.value.deletingBookId ?: return@launch
        mutableState.update { it.copy(deletingBookId = null) }
        container.bookRepository.deleteBook(id)
        eventChannel.send(LibraryEvent.Message("书籍已删除"))
    }

    fun requestMoveBook(bookId: String, bookTitle: String) =
        mutableState.update {
            it.copy(movingBook = MoveBookState(bookId, bookTitle, groupId))
        }

    fun cancelMoveBook() = mutableState.update { it.copy(movingBook = null) }

    fun confirmMoveBook(targetGroupId: String?) = viewModelScope.launch {
        val mb = mutableState.value.movingBook ?: return@launch
        container.bookRepository.moveBookToGroup(mb.bookId, targetGroupId)
        mutableState.update { it.copy(movingBook = null) }
        eventChannel.send(
            LibraryEvent.Message(
                if (targetGroupId == groupId) "已保留在当前分组"
                else if (targetGroupId == null) "已移回书架顶层"
                else "已移动"
            )
        )
    }
}

// =====================================
// 其它 ViewModel（Settings / Reader）
// =====================================

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
                mutableState.update { it.copy(settings = settings) }
            }
        }
        viewModelScope.launch {
            container.ttsPlaybackManager.state.collect { playback ->
                mutableState.update { it.copy(ttsPlayback = playback) }
            }
        }
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
        val st = mutableState.value
        val playback = st.ttsPlayback
        when {
            playback.bookId == bookId && playback.status in setOf(TtsPlaybackStatus.PLAYING, TtsPlaybackStatus.PREPARING) ->
                container.ttsPlaybackManager.pause()
            playback.bookId == bookId && playback.status == TtsPlaybackStatus.PAUSED ->
                container.ttsPlaybackManager.resume()
            else -> st.locator?.let { container.ttsPlaybackManager.play(bookId, it, st.settings) }
        }
    }

    private fun loadChapter(book: ParsedBook, index: Int) {
        val chapter = book.chapters[index]
        chapterJob?.cancel()
        chapterCache[chapter.href]?.let { cached ->
            mutableState.update { it.copy(content = cached, contentLoading = false, error = null) }
            return
        }
        mutableState.update { it.copy(content = null, contentLoading = true, error = null) }
        chapterJob = viewModelScope.launch {
            try {
                val content = container.bookRepository.readChapter(book, chapter)
                chapterCache[chapter.href] = content
                if (mutableState.value.locator?.chapterHref == chapter.href) {
                    mutableState.update { it.copy(content = content, contentLoading = false) }
                }
            } catch (error: Exception) {
                if (mutableState.value.locator?.chapterHref == chapter.href) {
                    mutableState.update {
                        it.copy(
                            contentLoading = false,
                            error = error.message ?: "无法解析本章内容",
                        )
                    }
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

class AppViewModelFactory(
    private val container: AppContainer,
    private val bookId: String? = null,
    private val groupId: String? = null,
    private val groupName: String? = null,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = when {
        modelClass.isAssignableFrom(LibraryViewModel::class.java) ->
            LibraryViewModel(container) as T

        modelClass.isAssignableFrom(SettingsViewModel::class.java) ->
            SettingsViewModel(container) as T

        modelClass.isAssignableFrom(GroupShelfViewModel::class.java) -> {
            val id = groupId ?: throw IllegalArgumentException("缺少 groupId 以创建 GroupShelfViewModel")
            val name = groupName ?: throw IllegalArgumentException("缺少 groupName 以创建 GroupShelfViewModel")
            GroupShelfViewModel(id, name, container) as T
        }

        modelClass.isAssignableFrom(ReaderViewModel::class.java) && bookId != null ->
            ReaderViewModel(bookId, container) as T

        else -> throw IllegalArgumentException("未知 ViewModel：${modelClass.name}")
    }
}
