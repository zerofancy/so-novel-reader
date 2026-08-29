package top.ntutn.sonovelreader.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import java.io.File
import top.ntutn.sonovelreader.data.GroupShelfUiState
import top.ntutn.sonovelreader.data.MoveBookState
import top.ntutn.sonovelreader.data.ShelfBook
import top.ntutn.sonovelreader.data.ShelfGroup

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupShelfScreen(
    state: GroupShelfUiState,
    groups: List<ShelfGroup>,    // 用于「移动到分组」对话框
    onBack: () -> Unit,
    onOpenBook: (String) -> Unit,
    onDeleteBook: (String) -> Unit,
    onConfirmDelete: () -> Unit,
    onCancelDelete: () -> Unit,
    onRenameGroup: () -> Unit,
    onRequestDeleteGroup: () -> Unit,
    onCancelDeleteGroup: () -> Unit,
    onConfirmDeleteGroup: () -> Unit,
    onDismissEditGroup: () -> Unit,
    onConfirmEditGroup: (String) -> Unit,
    onRequestMoveBook: (bookId: String, bookTitle: String) -> Unit,
    onCancelMoveBook: () -> Unit,
    onConfirmMoveBook: (targetGroupId: String?) -> Unit,
    onRequestMoveToNewGroup: (bookId: String) -> Unit,
    showRenameDialog: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Text(
                    state.groupName,
                    fontWeight = FontWeight.SemiBold,
                )
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "返回书架")
                }
            },
            actions = {
                var menuExpanded by remember { mutableStateOf(false) }
                Box {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "分组操作")
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text("重命名分组") },
                            onClick = {
                                menuExpanded = false
                                onRenameGroup()
                            },
                            leadingIcon = {
                                Icon(Icons.Default.Edit, contentDescription = null)
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("删除分组") },
                            onClick = {
                                menuExpanded = false
                                onRequestDeleteGroup()
                            },
                            leadingIcon = {
                                Icon(Icons.Default.Delete, contentDescription = null)
                            },
                        )
                    }
                }
            },
        )
        Box(Modifier.fillMaxSize()) {
            if (state.books.isEmpty()) {
                EmptyGroup()
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(112.dp),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 8.dp,
                        bottom = 24.dp,
                    ),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(state.books, key = ShelfBook::id) { book ->
                        GroupBookCard(
                            book = book,
                            onOpenBook = onOpenBook,
                            onDeleteBook = onDeleteBook,
                            onMoveBook = { onRequestMoveBook(book.id, book.title) },
                        )
                    }
                }
            }
        }
    }

    // ============ 对话框 ============

    // 删除书籍
    if (state.deletingBookId != null) {
        AlertDialog(
            onDismissRequest = onCancelDelete,
            title = { Text("删除书籍？") },
            text = { Text("书籍文件和阅读进度都会从本机删除，此操作无法撤销。") },
            confirmButton = { TextButton(onClick = onConfirmDelete) { Text("删除") } },
            dismissButton = { TextButton(onClick = onCancelDelete) { Text("取消") } },
            icon = { Icon(Icons.Default.Delete, contentDescription = null) },
        )
    }

    // 重命名分组（由上层透传 LibraryUiState.editingGroup 驱动）
    if (showRenameDialog) {
        var text by remember(state.groupName) { mutableStateOf(state.groupName) }
        AlertDialog(
            onDismissRequest = onDismissEditGroup,
            title = { Text("重命名分组") },
            text = {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("分组名") },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(onClick = { onConfirmEditGroup(text) }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = onDismissEditGroup) { Text("取消") }
            },
            properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = true),
        )
    }

    // 移动书籍
    val mb = state.movingBook
    if (mb != null) {
        MoveBookPickerDialog(
            moveState = mb,
            groups = groups,
            onDismiss = onCancelMoveBook,
            onConfirm = onConfirmMoveBook,
            onCreateNewGroup = { onRequestMoveToNewGroup(mb.bookId) },
        )
    }
}

// ================================================
// 空分组提示
// ================================================

@Composable
private fun EmptyGroup() {
    Column(
        Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            Icons.Default.Folder,
            contentDescription = null,
            modifier = Modifier.size(72.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(20.dp))
        Text(
            "这个分组还是空的",
            style = MaterialTheme.typography.headlineSmall,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "可以在书架顶层长按书籍卡片，选择「移动到分组」。",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ================================================
// 分组内的书籍卡片
// ================================================

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GroupBookCard(
    book: ShelfBook,
    onOpenBook: (String) -> Unit,
    onDeleteBook: (String) -> Unit,
    onMoveBook: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { onOpenBook(book.id) },
                onLongClick = { menuExpanded = true },
            ),
    ) {
        Box {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(166.dp),
                shape = RoundedCornerShape(10.dp),
            ) {
                if (book.coverPath != null) {
                    AsyncImage(
                        model = File(book.coverPath),
                        contentDescription = "${book.title}封面",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    GeneratedBookCover(
                        title = book.title,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp),
            ) {
                IconButton(
                    onClick = { menuExpanded = true },
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(
                            MaterialTheme.colorScheme.surface
                                .copy(alpha = 0.78f),
                        ),
                ) {
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription = "更多操作",
                    )
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                ) {
                    DropdownMenuItem(
                        text = { Text("移动到分组…") },
                        onClick = {
                            menuExpanded = false
                            onMoveBook()
                        },
                        leadingIcon = {
                            Icon(Icons.Default.DriveFileMove, contentDescription = null)
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("删除书籍") },
                        onClick = {
                            menuExpanded = false
                            onDeleteBook(book.id)
                        },
                        leadingIcon = {
                            Icon(Icons.Default.Delete, contentDescription = null)
                        },
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            book.title,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            fontWeight = FontWeight.Medium,
        )
        Text(
            book.authors,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// ================================================
// 移动书籍到分组 选择对话框（与 BookshelfScreen 相同逻辑，共享实现）
// ================================================

@Composable
private fun MoveBookPickerDialog(
    moveState: MoveBookState,
    groups: List<ShelfGroup>,
    onDismiss: () -> Unit,
    onConfirm: (targetGroupId: String?) -> Unit,
    onCreateNewGroup: () -> Unit = {},
) {
    val title = remember(moveState.bookTitle) {
        "移动《${moveState.bookTitle}》到…"
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                val currentIsTop = moveState.currentGroupId == null
                TextButton(
                    onClick = { onConfirm(null) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Start,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Default.Folder, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("书架顶层")
                        if (currentIsTop) {
                            Spacer(Modifier.weight(1f))
                            Text(
                                "（当前）",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
                groups.forEach { g ->
                    val selected = g.id == moveState.currentGroupId
                    TextButton(
                        onClick = { onConfirm(g.id) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Start,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Default.Folder, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(g.name)
                            Spacer(Modifier.width(4.dp))
                            Text(
                                "（${g.bookCount} 本）",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (selected) {
                                Spacer(Modifier.weight(1f))
                                Text(
                                    "（当前）",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }
                }
                // 新建分组
                if (groups.isEmpty()) {
                    Spacer(Modifier.height(4.dp))
                }
                TextButton(
                    onClick = onCreateNewGroup,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Start,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("新建分组")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
        dismissButton = { },
    )
}
