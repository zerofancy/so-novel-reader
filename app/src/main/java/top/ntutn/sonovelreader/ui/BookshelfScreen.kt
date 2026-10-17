package top.ntutn.sonovelreader.ui

import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import top.ntutn.sonovelreader.data.ShelfBook
import top.ntutn.sonovelreader.data.ShelfGroup

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookshelfScreen(
    state: LibraryUiState,
    onImport: () -> Unit,
    onOpenBook: (String) -> Unit,
    onDeleteBook: (String) -> Unit,
    onConfirmDelete: () -> Unit,
    onCancelDelete: () -> Unit,
    onOpenGroup: (ShelfGroup) -> Unit,
    onCreateGroup: () -> Unit,
    onRenameGroup: (String) -> Unit,
    onRequestDeleteGroup: (String) -> Unit,
    onCancelDeleteGroup: () -> Unit,
    onConfirmDeleteGroup: () -> Unit,
    onDismissEditGroup: () -> Unit,
    onConfirmEditGroup: (String) -> Unit,
    onRequestMoveBook: (bookId: String, bookTitle: String, currentGroupId: String?) -> Unit,
    onCancelMoveBook: () -> Unit,
    onConfirmMoveBook: (targetGroupId: String?) -> Unit,
    onRequestMoveToNewGroup: (bookId: String) -> Unit,
    onDropBook: (String, ShelfDropTarget) -> Unit,
    modifier: Modifier = Modifier,
) {
    var topMenuExpanded by remember { mutableStateOf(false) }
    var pairingBook by remember { mutableStateOf<String?>(null) }
    val grid = rememberLazyGridState()
    val dragEnabled = !state.importing && !state.organizing && !topMenuExpanded && pairingBook == null &&
        state.deletingBookId == null && state.deletingGroupId == null && state.editingGroup == null && state.movingBook == null
    val drag = rememberShelfDragState(grid, dragEnabled, state.groups to state.ungroupedBooks)
    Column(modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Text(
                    "我的书架",
                    fontWeight = FontWeight.SemiBold,
                )
            },
            actions = {
                Box {
                    IconButton(onClick = { topMenuExpanded = true }, enabled = !state.organizing) {
                        Icon(Icons.Default.MoreVert, contentDescription = "更多操作")
                    }
                    DropdownMenu(
                        expanded = topMenuExpanded,
                        onDismissRequest = { topMenuExpanded = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text("新建分组") },
                            onClick = {
                                topMenuExpanded = false
                                onCreateGroup()
                            },
                            leadingIcon = {
                                Icon(Icons.Default.Add, contentDescription = null)
                            },
                        )
                    }
                }
            },
        )
        Box(Modifier.fillMaxSize().shelfDragContainer(drag, onDropBook)) {
            val groupsEmpty = state.groups.isEmpty()
            val booksEmpty = state.ungroupedBooks.isEmpty()

            if (groupsEmpty && booksEmpty && !state.importing) {
                EmptyShelf(onImport)
            } else {
                LazyVerticalGrid(
                    state = grid,
                    columns = GridCells.Adaptive(112.dp),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 8.dp,
                        bottom = 104.dp,
                    ),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                    modifier = Modifier.fillMaxSize().testTag("shelf-grid"),
                ) {
                    // 1) 分组卡片
                    items(state.groups, key = ShelfGroup::id) { group ->
                        GroupCard(
                            group = group,
                            drag = drag,
                            enabled = !state.organizing,
                            onClick = { onOpenGroup(group) },
                            onRename = { onRenameGroup(group.id) },
                            onDelete = { onRequestDeleteGroup(group.id) },
                        )
                    }
                    // 2) 未分组书籍
                    items(state.ungroupedBooks, key = ShelfBook::id) { book ->
                        BookCard(
                            book = book,
                            drag = drag,
                            enabled = !state.organizing,
                            onPairBook = { pairingBook = book.id },
                            canPair = state.ungroupedBooks.size > 1,
                            onOpenBook = onOpenBook,
                            onDeleteBook = onDeleteBook,
                            onMoveBook = {
                                onRequestMoveBook(book.id, book.title, null)
                            },
                        )
                    }
                    // 3) "新建分组" 占位卡片
                    item(key = "__create_group__") {
                        AddGroupCard(onClick = onCreateGroup, enabled = !state.organizing)
                    }
                }
            }

            FloatingActionButton(
                onClick = { if (!state.organizing) onImport() },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(20.dp)
                    .onGloballyPositioned { drag.importButton = it.boundsInRoot() },
            ) {
                Icon(Icons.Default.Add, contentDescription = "导入 EPUB")
            }

            ShelfDragOverlay(drag)

            if (state.organizing) {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.scrim.copy(alpha = .12f))
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                        }
                    }, contentAlignment = Alignment.Center) {
                    Surface(shape = RoundedCornerShape(12.dp)) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(24.dp))
                            Spacer(Modifier.width(12.dp))
                            Text("正在整理…")
                        }
                    }
                }
            }
            if (state.importing) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.28f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Card {
                        Row(
                            Modifier.padding(20.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CircularProgressIndicator(
                                Modifier.size(28.dp),
                                strokeWidth = 3.dp,
                            )
                            Spacer(Modifier.width(14.dp))
                            Text("正在导入 EPUB…")
                        }
                    }
                }
            }
        }
    }

    pairingBook?.let { sourceId ->
        AlertDialog(
            onDismissRequest = { pairingBook = null },
            title = { Text("与其他书籍创建分类") },
            text = {
                LazyColumn {
                    items(state.ungroupedBooks.filter { it.id != sourceId }, key = ShelfBook::id) { book ->
                        TextButton(onClick = {
                            pairingBook = null
                            onDropBook(sourceId, ShelfDropTarget.Book(book.id))
                        }, modifier = Modifier.fillMaxWidth()) { Text(book.title) }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { pairingBook = null }) { Text("取消") } },
        )
    }

    // ========= 对话框 =========

    // 删除书籍
    if (state.deletingBookId != null) {
        AlertDialog(
            onDismissRequest = onCancelDelete,
            title = { Text("删除书籍？") },
            text = { Text("书籍文件和阅读进度都会从本机删除，此操作无法撤销。") },
            confirmButton = {
                TextButton(onClick = onConfirmDelete) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = onCancelDelete) { Text("取消") }
            },
            icon = { Icon(Icons.Default.Delete, contentDescription = null) },
        )
    }

    // 删除分组
    if (state.deletingGroupId != null) {
        val group = remember(state.groups, state.deletingGroupId) {
            state.groups.find { it.id == state.deletingGroupId }
        }
        AlertDialog(
            onDismissRequest = onCancelDeleteGroup,
            title = { Text("删除分组？") },
            text = {
                Text(
                    if (group != null) {
                        "删除「${group.name}」？\n组内的 ${group.bookCount} 本书会回到书架顶层，不会被删除。"
                    } else {
                        "分组不存在或已被删除"
                    }
                )
            },
            confirmButton = {
                TextButton(
                    onClick = onConfirmDeleteGroup,
                    enabled = group != null,
                ) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = onCancelDeleteGroup) { Text("取消") }
            },
            icon = { Icon(Icons.Default.Delete, contentDescription = null) },
        )
    }

    // 新建 / 重命名分组
    val eg = state.editingGroup
    if (eg != null) {
        EditGroupDialog(
            state = eg,
            onDismiss = onDismissEditGroup,
            onConfirm = onConfirmEditGroup,
        )
    }

    // 移动书籍到分组
    val mb = state.movingBook
    if (mb != null) {
        BookshelfMoveBookPickerDialog(
            moveState = mb,
            groups = state.groups,
            onDismiss = onCancelMoveBook,
            onConfirm = onConfirmMoveBook,
            onCreateNewGroup = { onRequestMoveToNewGroup(mb.bookId) },
        )
    }
}

// ================================================
// 空态
// ================================================

@Composable
private fun EmptyShelf(onImport: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            Icons.Default.AutoStories,
            contentDescription = null,
            modifier = Modifier.size(72.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(20.dp))
        Text("书架还是空的", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text(
            "导入 EPUB，开始一段安静的阅读时光",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        TextButton(onClick = onImport) { Text("选择书籍") }
    }
}
