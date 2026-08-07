package top.ntutn.sonovelreader.ui

import android.annotation.SuppressLint
import android.graphics.Color
import android.net.Uri
import android.view.MotionEvent
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.webkit.WebViewAssetLoader
import java.io.File
import org.json.JSONObject
import org.json.JSONTokener
import top.ntutn.sonovelreader.data.ReaderChapter
import top.ntutn.sonovelreader.data.ReaderSettings
import top.ntutn.sonovelreader.data.ReadingMode
import top.ntutn.sonovelreader.data.TocItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    viewModel: ReaderViewModel,
    onBack: () -> Unit,
    booksRoot: String,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var controlsVisible by remember { mutableStateOf(true) }
    var showToc by remember { mutableStateOf(false) }
    var pendingFragment by remember { mutableStateOf<String?>(null) }
    var jumpToken by remember { mutableIntStateOf(0) }
    val view = LocalView.current
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(state.settings.keepScreenOn, view) {
        val old = view.keepScreenOn
        view.keepScreenOn = state.settings.keepScreenOn
        onDispose { view.keepScreenOn = old }
    }
    DisposableEffect(controlsVisible, view) {
        val window = (view.context as android.app.Activity).window
        val controller = WindowInsetsControllerCompat(window, view)
        if (!controlsVisible) {
            controller.hide(WindowInsetsCompat.Type.statusBars())
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } else {
            controller.show(WindowInsetsCompat.Type.statusBars())
        }
        onDispose {
            // 离开阅读界面时恢复状态栏
            controller.show(WindowInsetsCompat.Type.statusBars())
        }
    }
    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) viewModel.flushProgress()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.flushProgress()
        }
    }
    BackHandler {
        if (showToc) showToc = false else onBack()
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        when {
            state.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
            state.error != null -> ReaderError(state.error.orEmpty(), viewModel::load, onBack)
            state.parsedBook != null && state.locator != null -> {
                val book = state.parsedBook!!
                val locator = state.locator!!
                val chapter = book.chapters[locator.chapterIndex.coerceIn(book.chapters.indices)]
                ReaderWebView(
                    bookId = book.book.id,
                    chapter = chapter,
                    settings = state.settings,
                    initialFraction = locator.chapterFraction,
                    fragment = pendingFragment,
                    jumpToken = jumpToken,
                    booksRoot = booksRoot,
                    onToggleControls = { controlsVisible = !controlsVisible },
                    onProgress = viewModel::updateFraction,
                    onFragmentConsumed = { pendingFragment = null },
                    onPreviousChapter = {
                        pendingFragment = null
                        viewModel.goToChapter(locator.chapterIndex - 1, 1f)
                    },
                    onNextChapter = {
                        pendingFragment = null
                        viewModel.goToChapter(locator.chapterIndex + 1, 0f)
                    },
                    modifier = Modifier.fillMaxSize(),
                )

                AnimatedVisibility(controlsVisible, Modifier.align(Alignment.TopCenter)) {
                    Surface(tonalElevation = 3.dp, shadowElevation = 3.dp) {
                        TopAppBar(
                            title = {
                                Column {
                                    Text(book.book.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        chapter.title,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            },
                            navigationIcon = {
                                IconButton(onClick = onBack) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回书架")
                                }
                            },
                            actions = {
                                IconButton(onClick = { showToc = true }) {
                                    Icon(Icons.AutoMirrored.Filled.FormatListBulleted, contentDescription = "目录")
                                }
                            },
                        )
                    }
                }

                AnimatedVisibility(controlsVisible, Modifier.align(Alignment.BottomCenter)) {
                    BottomAppBar(Modifier.navigationBarsPadding()) {
                        IconButton(
                            onClick = { viewModel.goToChapter(locator.chapterIndex - 1, 1f) },
                            enabled = locator.chapterIndex > 0,
                        ) { Icon(Icons.Default.ChevronLeft, contentDescription = "上一章") }
                        Spacer(Modifier.weight(1f))
                        Text(
                            "${locator.chapterIndex + 1} / ${book.chapters.size}",
                            style = MaterialTheme.typography.labelLarge,
                        )
                        Spacer(Modifier.width(8.dp))
                        IconButton(onClick = {
                            viewModel.flushProgress()
                            viewModel.setMode(
                                if (state.settings.readingMode == ReadingMode.SCROLL) ReadingMode.PAGED else ReadingMode.SCROLL,
                            )
                        }) {
                            Icon(
                                if (state.settings.readingMode == ReadingMode.SCROLL) Icons.Default.SwapHoriz else Icons.Default.SwapVert,
                                contentDescription = "切换阅读方式",
                            )
                        }
                        Spacer(Modifier.weight(1f))
                        IconButton(
                            onClick = { viewModel.goToChapter(locator.chapterIndex + 1, 0f) },
                            enabled = locator.chapterIndex < book.chapters.lastIndex,
                        ) { Icon(Icons.Default.ChevronRight, contentDescription = "下一章") }
                    }
                }

                if (showToc) {
                    ModalBottomSheet(onDismissRequest = { showToc = false }) {
                        Text(
                            "目录",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                        )
                        HorizontalDivider()
                        if (book.toc.isEmpty()) {
                            Text("这本书没有提供目录", Modifier.padding(24.dp))
                        } else {
                            LazyColumn(Modifier.fillMaxWidth()) {
                                items(book.toc) { item ->
                                    TocRow(
                                        item = item,
                                        selected = item.href == chapter.href,
                                        onClick = {
                                            val index = book.chapters.indexOfFirst { it.href == item.href }
                                            if (index >= 0) {
                                                pendingFragment = item.fragment
                                                jumpToken++
                                                viewModel.goToChapter(index, 0f)
                                                showToc = false
                                            }
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TocRow(item: TocItem, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick)
            .background(if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface)
            .padding(start = (20 + item.depth * 18).dp, end = 20.dp, top = 14.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(12.dp))
        Text(item.title, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
    }
}

@Composable
private fun ReaderError(message: String, onRetry: () -> Unit, onBack: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("无法打开书籍", style = MaterialTheme.typography.headlineSmall)
        Text(message, Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row { TextButton(onClick = onBack) { Text("返回书架") }; TextButton(onClick = onRetry) { Text("重试") } }
    }
}

@SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
@Composable
private fun ReaderWebView(
    bookId: String,
    chapter: ReaderChapter,
    settings: ReaderSettings,
    initialFraction: Float,
    fragment: String?,
    jumpToken: Int,
    booksRoot: String,
    onToggleControls: () -> Unit,
    onProgress: (Float) -> Unit,
    onFragmentConsumed: () -> Unit,
    onPreviousChapter: () -> Unit,
    onNextChapter: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val darkSystem = isSystemInDarkTheme()
    val loadKey = "$bookId|${chapter.href}|$settings|$jumpToken|$darkSystem"
    val assetLoader = remember(booksRoot) {
        WebViewAssetLoader.Builder()
            .addPathHandler("/books/", WebViewAssetLoader.InternalStoragePathHandler(context, File(booksRoot)))
            .build()
    }
    var downX by remember { mutableFloatStateOf(0f) }
    var downY by remember { mutableFloatStateOf(0f) }

    AndroidView(
        modifier = modifier,
        factory = {
            WebView(context).apply {
                setBackgroundColor(Color.TRANSPARENT)
                this.settings.apply {
                    javaScriptEnabled = true
                    allowFileAccess = false
                    allowContentAccess = false
                    blockNetworkLoads = true
                    domStorageEnabled = false
                    setSupportZoom(false)
                    builtInZoomControls = false
                    displayZoomControls = false
                    mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
                }
                isVerticalScrollBarEnabled = false
                isHorizontalScrollBarEnabled = false
                webViewClient = object : WebViewClient() {
                    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? =
                        assetLoader.shouldInterceptRequest(request.url)

                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                        return when (request.url.scheme) {
                            "sonovel" -> {
                                if (request.url.host == "next") onNextChapter() else onPreviousChapter()
                                true
                            }
                            "https" -> request.url.host != "appassets.androidplatform.net"
                            else -> true
                        }
                    }

                    override fun onPageFinished(view: WebView, url: String?) {
                        view.evaluateJavascript(
                            restorePositionScript(settings.readingMode, initialFraction, fragment),
                            null,
                        )
                        if (fragment != null) onFragmentConsumed()
                    }
                }
                setOnScrollChangeListener { web, scrollX, scrollY, _, _ ->
                    if (settings.readingMode == ReadingMode.SCROLL) {
                        val readerView = web as WebView
                        @Suppress("DEPRECATION")
                        val max = (readerView.contentHeight * readerView.scale - readerView.height).coerceAtLeast(1f)
                        onProgress((scrollY / max).coerceIn(0f, 1f))
                    }
                }
            }
        },
        update = { webView ->
            webView.setOnTouchListener { view, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        downX = event.x
                        downY = event.y
                        settings.readingMode == ReadingMode.PAGED
                    }
                    MotionEvent.ACTION_MOVE -> settings.readingMode == ReadingMode.PAGED
                    MotionEvent.ACTION_UP -> {
                        val deltaX = event.x - downX
                        val deltaY = event.y - downY
                        if (settings.readingMode == ReadingMode.PAGED) {
                            when {
                                kotlin.math.abs(deltaX) > 56 -> changePage(webView, if (deltaX < 0) 1 else -1, onProgress, onPreviousChapter, onNextChapter)
                                event.x < view.width * 0.32f -> changePage(webView, -1, onProgress, onPreviousChapter, onNextChapter)
                                event.x > view.width * 0.68f -> changePage(webView, 1, onProgress, onPreviousChapter, onNextChapter)
                                else -> onToggleControls()
                            }
                            view.performClick()
                            true
                        } else {
                            when {
                                kotlin.math.abs(deltaX) < 18 && kotlin.math.abs(deltaY) < 18 &&
                                    event.x in view.width * 0.3f..view.width * 0.7f -> onToggleControls()
                                deltaY < -90 && !webView.canScrollVertically(1) -> onNextChapter()
                                deltaY > 90 && !webView.canScrollVertically(-1) -> onPreviousChapter()
                            }
                            false
                        }
                    }
                    else -> settings.readingMode == ReadingMode.PAGED
                }
            }
            if (webView.tag != loadKey) {
                webView.tag = loadKey
                val chapterFile = File(chapter.absolutePath)
                val parent = chapter.href.substringBeforeLast('/', "")
                    .split('/').filter(String::isNotEmpty).joinToString("/") { Uri.encode(it) }
                val basePath = buildString {
                    append("https://appassets.androidplatform.net/books/")
                    append(Uri.encode(bookId))
                    append("/content/")
                    if (parent.isNotEmpty()) append(parent).append('/')
                }
                val html = buildReaderHtml(chapterFile, settings, darkSystem)
                webView.loadDataWithBaseURL(basePath, html, "text/html", Charsets.UTF_8.name(), null)
            }
        },
        onRelease = { it.destroy() },
    )
}

private fun changePage(
    webView: WebView,
    direction: Int,
    onProgress: (Float) -> Unit,
    onPreviousChapter: () -> Unit,
    onNextChapter: () -> Unit,
) {
    val script = """
        (() => {
          const height = window.innerHeight;
          const step = Math.max(height - 176, 1);
          const root = document.scrollingElement || document.documentElement;
          const max = Math.max(root.scrollHeight - height, 0);
          const current = window.scrollY;
          const target = current + (${direction}) * step;
          if (target < -1) return JSON.stringify({boundary:'previous', fraction:0});
          if (target > max + 1) return JSON.stringify({boundary:'next', fraction:1});
          const y = Math.max(0, Math.min(max, Math.round(target / step) * step));
          window.scrollTo({left:0, top:y, behavior:'smooth'});
          return JSON.stringify({boundary:'', fraction:max <= 0 ? 0 : y / max});
        })()
    """.trimIndent()
    webView.evaluateJavascript(script) { raw ->
        runCatching {
            val decoded = JSONTokener(raw).nextValue() as? String ?: raw
            JSONObject(decoded)
        }.onSuccess { result ->
            when (result.optString("boundary")) {
                "previous" -> onPreviousChapter()
                "next" -> onNextChapter()
                else -> onProgress(result.optDouble("fraction", 0.0).toFloat())
            }
        }
    }
}
