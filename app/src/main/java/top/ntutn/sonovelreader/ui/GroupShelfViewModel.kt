package top.ntutn.sonovelreader.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import top.ntutn.sonovelreader.AppContainer
import top.ntutn.sonovelreader.data.GroupShelfUiState
import top.ntutn.sonovelreader.data.MoveBookState

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
