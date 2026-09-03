package top.ntutn.sonovelreader.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RecordVoiceOver
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
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import top.ntutn.sonovelreader.data.ReadingMode
import top.ntutn.sonovelreader.tts.TtsPlaybackStatus
import top.ntutn.sonovelreader.tts.TtsSentence
import top.ntutn.sonovelreader.tts.progressAt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    viewModel: ReaderViewModel,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    onMessage: (String) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var controlsVisible by remember { mutableStateOf(true) }
    var showToc by remember { mutableStateOf(false) }
    var pendingFragment by remember { mutableStateOf<String?>(null) }
    var jumpToken by remember { mutableIntStateOf(0) }
    val view = LocalView.current
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val palette = readerPalette(state.settings.theme)
    val playback = state.ttsPlayback
    val activeSentence = playback.activeSentence
        ?.takeIf { playback.bookId == state.parsedBook?.book?.id && playback.status in setOf(TtsPlaybackStatus.PLAYING, TtsPlaybackStatus.PAUSED) }
    var followSuspended by remember { mutableStateOf(false) }
    LaunchedEffect(playback.bookId, playback.status) {
        if (playback.status == TtsPlaybackStatus.PREPARING) followSuspended = false
    }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) onMessage("未授予通知权限，后台朗读控制可能不会显示在通知栏")
        viewModel.toggleTts()
    }
    val toggleTts = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED &&
            playback.status !in setOf(TtsPlaybackStatus.PLAYING, TtsPlaybackStatus.PREPARING)
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            viewModel.toggleTts()
        }
    }

    LaunchedEffect(playback.error) {
        playback.error?.let(onMessage)
    }

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

    Box(Modifier.fillMaxSize().background(palette.background)) {
        when {
            state.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center), color = palette.foreground)
            state.error != null -> ReaderError(state.error.orEmpty(), viewModel::load, onBack, palette)
            state.parsedBook != null && state.locator != null && state.content != null -> {
                val book = state.parsedBook!!
                val locator = state.locator!!
                val content = state.content!!
                val chapter = book.chapters[locator.chapterIndex.coerceIn(book.chapters.indices)]
                val onPreviousChapter = {
                    followSuspended = true
                    pendingFragment = null
                    jumpToken++
                    viewModel.goToChapter(locator.chapterIndex - 1, 1f)
                }
                val onNextChapter = {
                    followSuspended = true
                    pendingFragment = null
                    jumpToken++
                    viewModel.goToChapter(locator.chapterIndex + 1, 0f)
                }

                LaunchedEffect(activeSentence?.locator, content, locator.chapterIndex, followSuspended) {
                    val sentence = activeSentence ?: return@LaunchedEffect
                    if (followSuspended) {
                        // 智能让出：等朗读进度追上用户当前阅读位置再恢复自动跟随
                        if (sentence.locator.chapterIndex == locator.chapterIndex &&
                            content.progressAt(sentence) >= locator.chapterFraction
                        ) {
                            followSuspended = false
                        }
                        return@LaunchedEffect
                    }
                    when {
                        sentence.locator.chapterIndex != locator.chapterIndex ->
                            viewModel.goToChapter(sentence.locator.chapterIndex, 0f)
                        else -> viewModel.updateFraction(content.progressAt(sentence))
                    }
                }

                if (state.settings.readingMode == ReadingMode.SCROLL) {
                    VerticalReader(
                        content = content,
                        settings = state.settings,
                        palette = palette,
                        chapterTitle = chapter.title,
                        initialFraction = locator.chapterFraction,
                        fragment = pendingFragment,
                        jumpToken = jumpToken,
                        hasPreviousChapter = locator.chapterIndex > 0,
                        hasNextChapter = locator.chapterIndex < book.chapters.lastIndex,
                        onToggleControls = { controlsVisible = !controlsVisible },
                        onProgress = viewModel::updateFraction,
                        onFragmentConsumed = { pendingFragment = null },
                        onPreviousChapter = onPreviousChapter,
                        onNextChapter = onNextChapter,
                        activeSentence = if (followSuspended) null else activeSentence?.locator,
                        onManualNavigation = { followSuspended = true },
                    )
                } else {
                    PagedReader(
                        content = content,
                        settings = state.settings,
                        palette = palette,
                        chapterTitle = chapter.title,
                        initialFraction = locator.chapterFraction,
                        fragment = pendingFragment,
                        jumpToken = jumpToken,
                        hasPreviousChapter = locator.chapterIndex > 0,
                        hasNextChapter = locator.chapterIndex < book.chapters.lastIndex,
                        onToggleControls = { controlsVisible = !controlsVisible },
                        onProgress = viewModel::updateFraction,
                        onFragmentConsumed = { pendingFragment = null },
                        onPreviousChapter = onPreviousChapter,
                        onNextChapter = onNextChapter,
                        activeSentence = if (followSuspended) null else activeSentence?.locator,
                        onManualNavigation = { followSuspended = true },
                    )
                }

                AnimatedVisibility(controlsVisible, Modifier.align(Alignment.TopCenter)) {
                    Surface(color = palette.background, tonalElevation = 3.dp, shadowElevation = 3.dp) {
                        TopAppBar(
                            title = {
                                Column {
                                    Text(book.book.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        chapter.title,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = palette.muted,
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
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = palette.background,
                                titleContentColor = palette.foreground,
                                navigationIconContentColor = palette.foreground,
                                actionIconContentColor = palette.foreground,
                            ),
                        )
                    }
                }

                AnimatedVisibility(controlsVisible, Modifier.align(Alignment.BottomCenter)) {
                    BottomAppBar(
                        modifier = Modifier.navigationBarsPadding(),
                        containerColor = palette.background,
                        contentColor = palette.foreground,
                    ) {
                        IconButton(
                            onClick = onPreviousChapter,
                            enabled = locator.chapterIndex > 0,
                        ) { Icon(Icons.Default.ChevronLeft, contentDescription = "上一章") }
                        Spacer(Modifier.weight(1f))
                        Text("${locator.chapterIndex + 1} / ${book.chapters.size}", style = MaterialTheme.typography.labelLarge)
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
                        IconButton(onClick = toggleTts) {
                            if (playback.bookId == book.book.id && playback.status == TtsPlaybackStatus.PREPARING) {
                                CircularProgressIndicator(Modifier.width(22.dp).height(22.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(
                                    if (playback.bookId == book.book.id && playback.status == TtsPlaybackStatus.PLAYING) {
                                        Icons.Default.Pause
                                    } else {
                                        Icons.Default.PlayArrow
                                    },
                                    contentDescription = if (playback.status == TtsPlaybackStatus.PLAYING) "暂停朗读" else "开始朗读",
                                )
                            }
                        }
                        IconButton(onClick = onOpenSettings) {
                            Icon(Icons.Default.RecordVoiceOver, contentDescription = "朗读设置")
                        }
                        Spacer(Modifier.weight(1f))
                        IconButton(
                            onClick = onNextChapter,
                            enabled = locator.chapterIndex < book.chapters.lastIndex,
                        ) { Icon(Icons.Default.ChevronRight, contentDescription = "下一章") }
                    }
                }

                if (showToc) {
                    ModalBottomSheet(onDismissRequest = { showToc = false }) {
                        val tocListState = rememberLazyListState()
                        val currentTocIndex = book.toc.indexOfFirst { it.href == chapter.href }
                        
                        LaunchedEffect(showToc) {
                            if (showToc && currentTocIndex >= 0) {
                                tocListState.scrollToItem(currentTocIndex)
                            }
                        }
                        
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
                            LazyColumn(Modifier.fillMaxWidth(), state = tocListState) {
                                items(book.toc) { item ->
                                    TocRow(
                                        item = item,
                                        selected = item.href == chapter.href,
                                        onClick = {
                                            val index = book.chapters.indexOfFirst { it.href == item.href }
                                            if (index >= 0) {
                                                followSuspended = true
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
            state.contentLoading -> CircularProgressIndicator(Modifier.align(Alignment.Center), color = palette.foreground)
        }
    }
}
