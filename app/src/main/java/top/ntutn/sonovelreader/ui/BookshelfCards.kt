package top.ntutn.sonovelreader.ui

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import java.io.File
import top.ntutn.sonovelreader.data.ShelfBook
import top.ntutn.sonovelreader.data.ShelfGroup

// ================================================
// 分组卡片
// ================================================

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun GroupCard(
    group: ShelfGroup,
    drag: ShelfDragState,
    enabled: Boolean,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val target = ShelfDropTarget.Group(group.id)
    LaunchedEffect(menuExpanded) { drag.openMenus[target] = menuExpanded }
    Column(
        Modifier.shelfDragCard(drag, ShelfDragItem(target, group.name))
            .fillMaxWidth()
            .combinedClickable(
                enabled = enabled,
                onClick = onClick,
                onLongClick = { menuExpanded = true },
            ),
    ) {
        Box {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(166.dp)
                    .testTag("shelf-group-${group.id}")
                    .shelfDropCover(drag, target),
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
                            enabled = enabled,
                            onClick = { menuExpanded = true },
                            modifier = Modifier
                                .onGloballyPositioned { drag.menus[target] = it.boundsInRoot() }
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
internal fun AddGroupCard(onClick: () -> Unit, enabled: Boolean) {
    Column(
        Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, enabled = enabled),
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
internal fun BookCard(
    book: ShelfBook,
    drag: ShelfDragState,
    enabled: Boolean,
    onPairBook: () -> Unit,
    canPair: Boolean,
    onOpenBook: (String) -> Unit,
    onDeleteBook: (String) -> Unit,
    onMoveBook: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val target = ShelfDropTarget.Book(book.id)
    LaunchedEffect(menuExpanded) { drag.openMenus[target] = menuExpanded }
    Column(
        Modifier.shelfDragCard(drag, ShelfDragItem(target, book.title, book))
            .testTag("shelf-book-${book.id}")
            .semantics {
                customActions = if (!enabled) emptyList() else buildList {
                    add(CustomAccessibilityAction("移动到分组") { onMoveBook(); true })
                    if (canPair) add(CustomAccessibilityAction("与其他书籍创建分类") { onPairBook(); true })
                }
            }
            .fillMaxWidth()
            .combinedClickable(
                enabled = enabled,
                onClick = { onOpenBook(book.id) },
            ),
    ) {
        Box {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(166.dp)
                    .testTag("shelf-cover-${book.id}")
                    .shelfDropCover(drag, target),
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
                    enabled = enabled,
                    onClick = { menuExpanded = true },
                    modifier = Modifier
                        .onGloballyPositioned { drag.menus[target] = it.boundsInRoot() }
                        .clip(RoundedCornerShape(50))
                        .background(
                            MaterialTheme.colorScheme.surface
                                .copy(alpha = 0.78f),
                        ),
                ) {
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription = "书籍『${book.title}』的更多操作",
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
                        text = { Text("与其他书籍创建分类") },
                        enabled = canPair,
                        onClick = { menuExpanded = false; onPairBook() },
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
