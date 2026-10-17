package top.ntutn.sonovelreader.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import top.ntutn.sonovelreader.data.EditGroupState
import top.ntutn.sonovelreader.data.MoveBookState
import top.ntutn.sonovelreader.data.ShelfGroup

// ================================================
// 新建 / 重命名分组对话框
// ================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EditGroupDialog(
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
internal fun BookshelfMoveBookPickerDialog(
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
