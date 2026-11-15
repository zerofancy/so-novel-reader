package top.ntutn.sonovelreader.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.ntutn.sonovelreader.BuildConfig

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    var retry by remember { mutableStateOf(0) }
    val catalog by produceState<Result<CreditsCatalog>?>(null, context.assets, retry) {
        value = withContext(Dispatchers.IO) { runCatching { CreditsRepository.load(context.assets) } }
    }
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(title = { Text("关于") }, navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回阅读设置")
                }
            })
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).testTag("about-screen"),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Column(Modifier.padding(16.dp)) {
                    Text(context.applicationInfo.loadLabel(context.packageManager).toString(),
                        style = MaterialTheme.typography.headlineSmall)
                    Text("版本 ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                }
            }
            if (catalog == null) item { Text("正在读取离线声明…", Modifier.padding(16.dp)) }
            catalog?.exceptionOrNull()?.let {
                item {
                    Column(Modifier.padding(16.dp)) {
                        Text("离线声明读取失败，请重试", color = MaterialTheme.colorScheme.error)
                        TextButton(onClick = { retry++ }) { Text("重试") }
                    }
                }
            }
            catalog?.getOrNull()?.let { credits ->
                item { GroupTitle("内置字体") }
                items(credits.fonts, key = { "font-${it.id}" }) { CreditCard(it, snackbar) }
                item { GroupTitle("第三方库") }
                items(credits.libraries, key = { "library-${it.id}" }) { CreditCard(it, snackbar) }
                item { Text("仅列出随应用分发的组件，不包括您私有导入的字体。",
                    Modifier.padding(16.dp), style = MaterialTheme.typography.bodySmall) }
            }
        }
    }
}

@Composable
private fun GroupTitle(title: String) {
    Text(title, Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.titleLarge)
}

@Suppress("DEPRECATION")
@Composable
private fun CreditCard(entry: CreditEntry, snackbar: SnackbarHostState) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    var expanded by rememberSaveable(entry.id) { mutableStateOf(false) }
    var text by remember(entry.id) { mutableStateOf<Result<String>?>(null) }
    LaunchedEffect(expanded, entry.id) {
        if (expanded && text == null) {
            text = withContext(Dispatchers.IO) { runCatching { CreditsRepository.text(context.assets, entry) } }
        }
    }
    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp).testTag("credit-${entry.id}")) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SelectionContainer {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(entry.name, style = MaterialTheme.typography.titleMedium)
                    Text("版本：${entry.version}")
                    Text("作者 / 维护者：${entry.authors}")
                    Text(entry.copyright)
                    Text("LICENSE：${entry.license}")
                }
            }
            listOf("项目 / 源码" to entry.projectUrl, "上游 LICENSE" to entry.licenseUrl,
                "来源" to entry.sourceUrl).filter { it.second.isNotBlank() }.distinctBy { it.second }
                .forEach { (label, url) ->
                    SelectionContainer { Text(url, style = MaterialTheme.typography.bodySmall) }
                    TextButton(onClick = {
                        val uri = Uri.parse(url)
                        if (uri.scheme !in listOf("https", "http") || uri.host.isNullOrBlank()) {
                            scope.launch { snackbar.showSnackbar("链接不可用，可复制链接后查看") }
                        } else {
                            try {
                                context.startActivity(Intent(Intent.ACTION_VIEW, uri)
                                    .addCategory(Intent.CATEGORY_BROWSABLE)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                            } catch (_: ActivityNotFoundException) {
                                scope.launch { snackbar.showSnackbar("无法打开浏览器，可复制链接后查看") }
                            } catch (_: SecurityException) {
                                scope.launch { snackbar.showSnackbar("无法打开链接，可复制链接后查看") }
                            }
                        }
                    }) { Text("打开$label") }
                }
            if (entry.texts.isEmpty()) {
                Text("离线授权文本尚待发布前核对补齐", color = MaterialTheme.colorScheme.error)
            }
            TextButton(onClick = { expanded = !expanded }, enabled = entry.texts.isNotEmpty()) {
                Text(if (expanded) "收起 LICENSE / NOTICE" else "展开 LICENSE / NOTICE（离线）")
            }
            if (expanded) {
                when {
                    text == null -> Text("正在读取…")
                    text?.isFailure == true -> {
                        Text("授权文本读取失败", color = MaterialTheme.colorScheme.error)
                        TextButton(onClick = { expanded = false; text = null }) { Text("收起后重试") }
                    }
                    else -> {
                        TextButton(onClick = {
                            clipboard.setText(AnnotatedString(text!!.getOrThrow()))
                            scope.launch { snackbar.showSnackbar("已复制 LICENSE / NOTICE") }
                        }) { Text("复制完整授权文本") }
                        SelectionContainer {
                            Text(text!!.getOrThrow(), Modifier.fillMaxWidth().heightIn(max = 400.dp)
                                .verticalScroll(rememberScrollState()), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}
