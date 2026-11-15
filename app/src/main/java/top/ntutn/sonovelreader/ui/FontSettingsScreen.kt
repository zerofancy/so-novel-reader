package top.ntutn.sonovelreader.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import top.ntutn.sonovelreader.data.ReaderAppearance
import top.ntutn.sonovelreader.data.fonts.FontCatalogState
import top.ntutn.sonovelreader.data.fonts.FontEntry
import top.ntutn.sonovelreader.data.fonts.FontSource

@Composable
fun FontSettingsScreen(viewModel: FontSettingsViewModel, onBack: () -> Unit, onMessage: (String) -> Unit) {
    val catalog by viewModel.catalog.collectAsStateWithLifecycle()
    val appearance by viewModel.appearance.collectAsStateWithLifecycle()
    val selecting by viewModel.selecting.collectAsStateWithLifecycle()
    val importing by viewModel.importing.collectAsStateWithLifecycle()
    val deleting by viewModel.deleting.collectAsStateWithLifecycle()
    val focus by viewModel.focus.collectAsStateWithLifecycle()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument(), viewModel::importFont)
    LaunchedEffect(viewModel) { viewModel.events.collect(onMessage) }
    FontSettingsContent(catalog, appearance, selecting, importing, deleting, focus, onBack,
        onImport = { all -> picker.launch(if (all) arrayOf("*/*") else arrayOf(
            "font/ttf", "font/otf", "application/x-font-ttf", "application/x-font-opentype", "application/octet-stream")) },
        onSelect = viewModel::select, onDelete = viewModel::delete, onRetry = { viewModel.refresh() })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FontSettingsContent(
    catalog: FontCatalogState,
    appearance: ReaderAppearance?,
    selecting: String?,
    importing: Boolean,
    deleting: Boolean,
    focus: FontFocusRequest?,
    onBack: () -> Unit,
    onImport: (Boolean) -> Unit,
    onSelect: (String) -> Unit,
    onDelete: (String) -> Unit,
    onRetry: () -> Unit,
) {
    val list = rememberLazyListState()
    var deleteTarget by remember { mutableStateOf<FontEntry?>(null) }
    LaunchedEffect(focus) {
        val index = catalog.entries.indexOfFirst { it.id == focus?.id }
        // Preview + help items precede the flat catalog, with headers represented in each row.
        if (index >= 0) list.animateScrollToItem(index + 2)
    }
    Scaffold(topBar = {
        TopAppBar(title = { Text("阅读字体") }, navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回阅读设置") }
        }, actions = {
            TextButton(onClick = { onImport(false) }, enabled = !importing && !catalog.loading && catalog.error == null) {
                Text(if (importing) "正在导入…" else "导入字体")
            }
        })
    }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).testTag("font-settings"), state = list) {
            item {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("预览", style = MaterialTheme.typography.titleMedium)
                    if (appearance == null) CircularProgressIndicator() else {
                        val palette = readerPalette(appearance.settings.theme)
                        CompositionLocalProvider(LocalReaderFont provides appearance.font.family) {
                            Column(Modifier.fillMaxWidth().heightIn(max = 260.dp).background(palette.background)
                                .verticalScroll(rememberScrollState()).padding(16.dp).testTag("font-preview"),
                                verticalArrangement = Arrangement.spacedBy(appearance.settings.paragraphSpacingDp.dp)) {
                                Text("山色入帘青，灯下读书声。", style = readerTextStyle(appearance.settings, palette.foreground))
                                Text("春风又绿江南岸。0123456789 Aa，。！？", style = readerTextStyle(appearance.settings, palette.foreground))
                            }
                        }
                    }
                }
            }
            item {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    Text("支持静态 TTF、OTF，单个文件不超过 50 MiB。仅在本机使用，请确保拥有字体使用权。",
                        style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = { onImport(true) }, enabled = !importing && !catalog.loading && catalog.error == null) {
                        Text("找不到文件？选择所有文件")
                    }
                }
            }
            items(catalog.entries, key = { it.id }) { font ->
                Column {
                    if (font.id == catalog.entries.firstOrNull { it.source == FontSource.BUILTIN }?.id)
                        Text("内置字体", Modifier.padding(16.dp), style = MaterialTheme.typography.titleMedium)
                    if (font.id == catalog.entries.firstOrNull { it.source == FontSource.IMPORTED }?.id)
                        Text("我的字体", Modifier.padding(16.dp), style = MaterialTheme.typography.titleMedium)
                    FontChoice(font, appearance?.font?.id == font.id, selecting == font.id, !deleting,
                        { onSelect(font.id) }, { deleteTarget = font })
                }
            }
            if (catalog.loading) item { CircularProgressIndicator(Modifier.padding(16.dp)) }
            if (catalog.error != null) item {
                Column(Modifier.padding(16.dp)) {
                    Text(catalog.error.userMessage, color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = onRetry) { Text("重试") }
                }
            } else if (!catalog.loading && catalog.entries.none { it.source == FontSource.IMPORTED }) item {
                Text("我的字体", Modifier.padding(16.dp), style = MaterialTheme.typography.titleMedium)
                Text("还没有导入字体，支持 TTF、OTF", Modifier.padding(16.dp))
            }
            item { TextButton(onClick = onRetry, modifier = Modifier.padding(16.dp)) { Text("重新读取字体") } }
        }
    }
    deleteTarget?.let { font ->
        AlertDialog(onDismissRequest = { if (!deleting) deleteTarget = null }, title = { Text("删除字体『${font.displayName}』？") },
            text = { Text("此操作不会删除原始文件。" + if (appearance?.font?.id == font.id) "删除后将改用系统默认。" else "") },
            confirmButton = { TextButton(onClick = { onDelete(font.id); deleteTarget = null }, enabled = !deleting) { Text("删除") } },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("取消") } })
    }
}

@Composable
private fun FontChoice(font: FontEntry, selected: Boolean, loading: Boolean, enabled: Boolean,
    onSelect: () -> Unit, onDelete: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).testTag("font-${font.id}")
        .selectable(selected, enabled = enabled, role = Role.RadioButton, onClick = onSelect).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected, onClick = null)
        Column(Modifier.weight(1f).padding(12.dp)) {
            Text(font.displayName, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (font.source == FontSource.IMPORTED) Text("${font.originalName} · ${font.sha256.take(8)}",
                style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (loading) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
        if (font.source == FontSource.IMPORTED) Box {
            IconButton(onClick = { menu = true }, enabled = enabled) { Icon(Icons.Default.MoreVert, "管理字体 ${font.displayName}") }
            DropdownMenu(menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("删除") }, onClick = { menu = false; onDelete() })
            }
        }
    }
}
