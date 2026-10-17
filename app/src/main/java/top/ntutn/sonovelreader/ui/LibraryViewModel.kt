package top.ntutn.sonovelreader.ui

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import top.ntutn.sonovelreader.AppContainer
import top.ntutn.sonovelreader.data.EditGroupState
import top.ntutn.sonovelreader.data.ImportBatchResult
import top.ntutn.sonovelreader.data.MoveBookState
import top.ntutn.sonovelreader.data.ShelfBook
import top.ntutn.sonovelreader.data.ShelfGroup

data class LibraryUiState(
    val groups: List<ShelfGroup> = emptyList(),
    val ungroupedBooks: List<ShelfBook> = emptyList(),
    val importing: Boolean = false,
    val organizing: Boolean = false,
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

    fun dropBook(bookId: String, target: ShelfDropTarget) {
        if (mutableState.value.organizing || mutableState.value.importing) return
        mutableState.update { it.copy(organizing = true) }
        viewModelScope.launch {
            val message = try {
                when (target) {
                    is ShelfDropTarget.Group -> "已移入『${container.bookRepository.dropBookIntoGroup(bookId, target.id)}』"
                    is ShelfDropTarget.Book -> "已创建『${container.bookRepository.groupTwoBooks(bookId, target.id)}』，并移入 2 本书"
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: top.ntutn.sonovelreader.data.local.ShelfChangedException) {
                error.message!!
            } catch (error: Exception) {
                "整理失败，请重试"
            } finally {
                mutableState.update { it.copy(organizing = false) }
            }
            eventChannel.send(LibraryEvent.Message(message))
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
