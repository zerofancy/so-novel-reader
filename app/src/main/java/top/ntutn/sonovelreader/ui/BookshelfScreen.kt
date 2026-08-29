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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import top.ntutn.sonovelreader.data.EditGroupState
import top.ntutn.sonovelreader.data.MoveBookState
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
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize()) {
        var topMenuExpanded by remember { mutableStateOf(false) }
        TopAppBar(
            title = {
                Text(
                    "我的书架",
                    fontWeight = FontWeight.SemiBold,
                )
            },
            actions = {
                Box {
                    IconButton(onClick = { topMenuExpanded = true }) {
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
        Box(Modifier.fillMaxSize()) {
            val groupsEmpty = state.groups.isEmpty()
            val booksEmpty = state.ungroupedBooks.isEmpty()

            if (groupsEmpty && booksEmpty && !state.importing) {
                EmptyShelf(onImport)
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(112.dp),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 8.dp,
                        bottom = 104.dp,
                    ),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    // 1) 分组卡片
                    items(state.groups, key = ShelfGroup::id) { group ->
                        GroupCard(
                            group = group,
                            onClick = { onOpenGroup(group) },
                            onRename = { onRenameGroup(group.id) },
                            onDelete = { onRequestDeleteGroup(group.id) },
                        )
                    }
                    // 2) 未分组书籍
                    items(state.ungroupedBooks, key = ShelfBook::id) { book ->
                        BookCard(
                            book = book,
                            onOpenBook = onOpenBook,
                            onDeleteBook = onDeleteBook,
                            onMoveBook = {
                                onRequestMoveBook(book.id, book.title, null)
                            },
                        )
                    }
                    // 3) "新建分组" 占位卡片
                    item(key = "__create_group__") {
                        AddGroupCard(onClick = onCreateGroup)
                    }
                }
            }

            FloatingActionButton(
                onClick = onImport,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(20.dp),
            ) {
                Icon(Icons.Default.Add, contentDescription = "导入 EPUB")
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
        MoveBookPickerDialog(
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

// ================================================
// 分组卡片
// ================================================

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GroupCard(
    group: ShelfGroup,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
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
                Column(
                    Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(
                        Icons.Default.Folder,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        group.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }
            // 右上角：数量角标 + 更多按钮
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.height(20.dp),
                    ) {
                        Text(
                            "${group.bookCount}",
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        )
                    }
                    Spacer(Modifier.width(2.dp))
                    Box {
                        IconButton(
                            onClick = { menuExpanded = true },
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(
                                    MaterialTheme.colorScheme.surface
                                        .copy(alpha = 0.78f),
                                ),
                        ) {
                            Icon(
                                Icons.Default.MoreVert,
                                contentDescription = "分组「${group.name}」的更多操作",
                                modifier = Modifier.size(16.dp),
                            )
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text("重命名分组") },
                                onClick = {
                                    menuExpanded = false
                                    onRename()
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.Edit, contentDescription = null)
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("删除分组") },
                                onClick = {
                                    menuExpanded = false
                                    onDelete()
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.Delete, contentDescription = null)
                                },
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        // 名字占位：为了与书籍卡片底部的两行标题对齐，保持布局平衡
        Text(
            group.name,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontWeight = FontWeight.Medium,
        )
        Text(
            "分组 · ${group.bookCount} 本",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// ================================================
// "新建分组" 卡片
// ================================================

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AddGroupCard(onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick),
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(166.dp),
            shape = RoundedCornerShape(10.dp),
        ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .background(
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    )
                    .padding(12.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "新建分组",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "新建分组",
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            " ", // 占位：保持与书籍/分组卡片相同两行高度
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

// ================================================
// 书籍卡片
// ================================================

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BookCard(
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
// 新建 / 重命名分组对话框
// ================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditGroupDialog(
    state: EditGroupState,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var text by remember(state) { mutableStateOf(state.initialName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (state.editingId == null) "新建分组"
                else "重命名分组"
            )
        },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("分组名") },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text) }) { Text("确定") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
        properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = true),
    )
}

// ================================================
// 移动书籍到分组 选择对话框
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
                // 顶层
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
                        Icon(Icons.Default.AutoStories, contentDescription = null)
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
