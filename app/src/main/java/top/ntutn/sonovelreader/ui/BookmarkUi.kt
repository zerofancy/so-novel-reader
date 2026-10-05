package top.ntutn.sonovelreader.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import top.ntutn.sonovelreader.data.DuplicateBookmarkException
import top.ntutn.sonovelreader.data.validBookmarkName
import top.ntutn.sonovelreader.data.local.BookmarkEntity

@Composable
internal fun BookmarkNameDialog(
    initialName: String,
    editing: Boolean,
    onDismiss: () -> Unit,
    onSave: suspend (String) -> Unit,
    onMessage: (String) -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val valid = validBookmarkName(name)
    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text(if (editing) "重命名书签" else "添加书签") },
        text = {
            Column {
                OutlinedTextField(value = name, onValueChange = { name = it; error = null },
                    enabled = !saving, singleLine = true, label = { Text("书签名称") },
                    isError = !valid || error != null,
                    supportingText = { Text(error ?: "请输入 1–50 个字符，名称不能全为空白") })
            }
        },
        dismissButton = { TextButton(enabled = !saving, onClick = onDismiss) { Text("取消") } },
        confirmButton = {
            TextButton(enabled = valid && !saving, onClick = {
                saving = true
                scope.launch {
                    try {
                        onSave(name.trim())
                        onMessage(if (editing) "书签已重命名" else "书签已保存")
                        onDismiss()
                    } catch (cancelled: CancellationException) { throw cancelled
                    } catch (_: DuplicateBookmarkException) { error = "该位置已有书签，可在书签列表中重命名"
                    } catch (_: Exception) { error = "保存失败，请重试"
                    } finally { saving = false }
                }
            }) { Text(if (saving) "保存中…" else "保存") }
        },
    )
}

@Composable
internal fun BookmarkList(
    state: ReaderUiState,
    listState: LazyListState,
    viewModel: ReaderViewModel,
    onJump: (BookmarkEntity) -> Unit,
    onMessage: (String) -> Unit,
) {
    var editing by remember { mutableStateOf<BookmarkEntity?>(null) }
    var deleting by remember { mutableStateOf<BookmarkEntity?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    when {
        state.bookmarksLoading -> CircularProgressIndicator(Modifier.padding(24.dp))
        state.bookmarksError != null -> Column(Modifier.padding(24.dp)) {
            Text(state.bookmarksError)
            TextButton(onClick = viewModel::observeBookmarks) { Text("重试") }
        }
        state.bookmarks.isEmpty() -> Column(Modifier.padding(24.dp)) {
            Text("还没有书签")
            Text("阅读时点击添加书签，记录当前位置")
        }
        else -> LazyColumn(Modifier.fillMaxWidth(), state = listState) {
            items(state.bookmarks, key = { it.id }) { bookmark ->
                var menu by remember { mutableStateOf(false) }
                Row(Modifier.fillMaxWidth().clickable { onJump(bookmark) }.padding(start = 24.dp, end = 8.dp, top = 8.dp, bottom = 8.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(bookmark.name, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text("${bookmark.chapterTitle} · ${(bookmark.chapterFraction * 100).toInt()}%",
                            style = MaterialTheme.typography.bodySmall)
                    }
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "书签更多操作") }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text("重命名") }, onClick = { menu = false; editing = bookmark })
                            DropdownMenuItem(text = { Text("删除") }, onClick = { menu = false; deleting = bookmark })
                        }
                    }
                }
            }
        }
    }
    editing?.let { bookmark ->
        BookmarkNameDialog(bookmark.name, true, { editing = null },
            { viewModel.renameBookmark(bookmark.id, it) }, onMessage)
    }
    deleting?.let { bookmark ->
        AlertDialog(onDismissRequest = { if (!busy) deleting = null },
            title = { Text("删除书签") }, text = { Text("删除书签『${bookmark.name}』？") },
            dismissButton = { TextButton(enabled = !busy, onClick = { deleting = null }) { Text("取消") } },
            confirmButton = { TextButton(enabled = !busy, onClick = {
                busy = true
                scope.launch {
                    try { viewModel.deleteBookmark(bookmark.id); deleting = null
                    } catch (cancelled: CancellationException) { throw cancelled
                    } catch (_: Exception) { onMessage("删除失败，请重试")
                    } finally { busy = false }
                }
            }) { Text("删除") } })
    }
}
