package top.ntutn.sonovelreader.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.BrokenImage
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.Velocity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import coil3.compose.SubcomposeAsyncImage
import java.io.File
import kotlin.math.abs
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import top.ntutn.sonovelreader.data.ReaderBlock
import top.ntutn.sonovelreader.data.ReaderContent
import top.ntutn.sonovelreader.data.ReaderPageItem
import top.ntutn.sonovelreader.data.ReaderSettings
import top.ntutn.sonovelreader.data.ReaderTheme
import top.ntutn.sonovelreader.data.ReadingMode
import top.ntutn.sonovelreader.data.TocItem
import top.ntutn.sonovelreader.tts.TtsPlaybackStatus
import top.ntutn.sonovelreader.tts.TtsSentenceLocator
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
                    viewModel.stopTtsForManualNavigation()
                    pendingFragment = null
                    viewModel.goToChapter(locator.chapterIndex - 1, 1f)
                }
                val onNextChapter = {
                    viewModel.stopTtsForManualNavigation()
                    pendingFragment = null
                    viewModel.goToChapter(locator.chapterIndex + 1, 0f)
                }

                LaunchedEffect(activeSentence?.locator, content, locator.chapterIndex) {
                    val sentence = activeSentence ?: return@LaunchedEffect
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
                        activeSentence = activeSentence?.locator,
                        onManualNavigation = viewModel::stopTtsForManualNavigation,
                    )
                } else {
                    PagedReader(
                        content = content,
                        settings = state.settings,
                        palette = palette,
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
                        activeSentence = activeSentence?.locator,
                        onManualNavigation = viewModel::stopTtsForManualNavigation,
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
                            onClick = { viewModel.goToChapter(locator.chapterIndex - 1, 1f) },
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
                                                viewModel.stopTtsForManualNavigation()
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

@Composable
internal fun VerticalReader(
    content: ReaderContent,
    settings: ReaderSettings,
    palette: ReaderPalette,
    initialFraction: Float,
    fragment: String?,
    jumpToken: Int,
    hasPreviousChapter: Boolean,
    hasNextChapter: Boolean,
    onToggleControls: () -> Unit,
    onProgress: (Float) -> Unit,
    onFragmentConsumed: () -> Unit,
    onPreviousChapter: () -> Unit,
    onNextChapter: () -> Unit,
    activeSentence: TtsSentenceLocator? = null,
    onManualNavigation: () -> Unit = {},
) {
    val listState = rememberLazyListState()
    val textStyle = readerTextStyle(settings, palette.foreground)
    val density = LocalDensity.current
    val thresholdPx = with(density) { CHAPTER_PULL_THRESHOLD.roundToPx().toFloat() }
    val maxDistancePx = with(density) { CHAPTER_PULL_MAX_DISTANCE.roundToPx().toFloat() }
    val pullState = remember(content, hasPreviousChapter, hasNextChapter, thresholdPx, maxDistancePx) {
        ChapterPullState(thresholdPx, maxDistancePx)
    }
    val nestedScrollConnection = remember(pullState, listState, hasPreviousChapter, hasNextChapter) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput && available.y != 0f) onManualNavigation()
                val consumed = pullState.consumePreScroll(
                    deltaY = available.y,
                    isUserInput = source == NestedScrollSource.UserInput,
                )
                return Offset(0f, consumed)
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                val consumedY = pullState.consumePostScroll(
                    deltaY = available.y,
                    atStart = !listState.canScrollBackward,
                    atEnd = !listState.canScrollForward,
                    hasPreviousChapter = hasPreviousChapter,
                    hasNextChapter = hasNextChapter,
                    isUserInput = source == NestedScrollSource.UserInput,
                )
                return Offset(0f, consumedY)
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                val hadActivePull = pullState.isActive
                when (pullState.release()) {
                    ChapterPullEdge.PREVIOUS -> onPreviousChapter()
                    ChapterPullEdge.NEXT -> onNextChapter()
                    ChapterPullEdge.NONE -> Unit
                }
                return if (hadActivePull) available else Velocity.Zero
            }
        }
    }
    val pullOffsetPx by animateFloatAsState(
        targetValue = pullState.signedDistancePx,
        animationSpec = if (pullState.isActive) {
            snap()
        } else {
            spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMediumLow,
            )
        },
        label = "chapterPullOffset",
    )

    LaunchedEffect(content, jumpToken) {
        val target = fragment?.let(content.anchors::get) ?: content.positionAt(initialFraction)
        listState.scrollToItem((target.blockIndex + 1).coerceAtLeast(0))
        if (fragment != null) onFragmentConsumed()
    }
    LaunchedEffect(content, listState) {
        snapshotFlow {
            listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index in 1..content.blocks.size }
        }.distinctUntilChanged().collect { visible ->
            if (visible != null) {
                val blockIndex = visible.index - 1
                val inside = (-visible.offset).toFloat().div(visible.size.coerceAtLeast(1)).coerceIn(0f, 1f)
                onProgress(content.progressAt(blockIndex, inside))
            }
        }
    }

    Box(Modifier.fillMaxSize().clipToBounds()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize()
                .nestedScroll(nestedScrollConnection)
                .graphicsLayer { translationY = pullOffsetPx }
                .pointerInput(onToggleControls) {
                    detectTapGestures { position ->
                        if (position.x in size.width * 0.3f..size.width * 0.7f) onToggleControls()
                    }
                },
            contentPadding = PaddingValues(start = 22.dp, end = 22.dp, top = 84.dp, bottom = 92.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            overscrollEffect = null,
        ) {
            item(key = "previous-chapter") {
                ChapterNavigation(
                    label = "上一章",
                    enabled = hasPreviousChapter,
                    onClick = onPreviousChapter,
                    palette = palette,
                    edge = ChapterPullEdge.PREVIOUS,
                    pullProgress = if (pullState.edge == ChapterPullEdge.PREVIOUS) pullState.progress else 0f,
                    armed = pullState.edge == ChapterPullEdge.PREVIOUS && pullState.isArmed,
                )
            }
            itemsIndexed(content.blocks, key = { index, _ -> "block-$index" }) { blockIndex, block ->
                ReaderBlockView(blockIndex, block, textStyle, palette, activeSentence)
            }
            item(key = "next-chapter") {
                ChapterNavigation(
                    label = "下一章",
                    enabled = hasNextChapter,
                    onClick = onNextChapter,
                    palette = palette,
                    edge = ChapterPullEdge.NEXT,
                    pullProgress = if (pullState.edge == ChapterPullEdge.NEXT) pullState.progress else 0f,
                    armed = pullState.edge == ChapterPullEdge.NEXT && pullState.isArmed,
                )
            }
        }
    }
}

@Composable
internal fun PagedReader(
    content: ReaderContent,
    settings: ReaderSettings,
    palette: ReaderPalette,
    initialFraction: Float,
    fragment: String?,
    jumpToken: Int,
    hasPreviousChapter: Boolean,
    hasNextChapter: Boolean,
    onToggleControls: () -> Unit,
    onProgress: (Float) -> Unit,
    onFragmentConsumed: () -> Unit,
    onPreviousChapter: () -> Unit,
    onNextChapter: () -> Unit,
    activeSentence: TtsSentenceLocator? = null,
    onManualNavigation: () -> Unit = {},
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val textMeasurer = rememberTextMeasurer()
        val textStyle = readerTextStyle(settings, palette.foreground)
        val widthPx = with(density) { (maxWidth - 44.dp).roundToPx().coerceAtLeast(1) }
        val heightPx = with(density) { (maxHeight - 176.dp).roundToPx().coerceAtLeast(1) }
        val spacingPx = with(density) { 14.dp.roundToPx() }
        val lineHeightPx = with(density) { (settings.fontSizeSp * settings.lineHeight).sp.roundToPx().coerceAtLeast(1) }
        val pages = remember(content, widthPx, heightPx, spacingPx, textStyle, textMeasurer) {
            paginateReaderContent(content, widthPx, heightPx, spacingPx) { text, availableWidth, availableHeight ->
                val maxLines = (availableHeight / lineHeightPx).coerceAtLeast(0)
                if (maxLines == 0) {
                    MeasuredTextSlice(0, 0)
                } else {
                    val layout = textMeasurer.measure(
                        text = AnnotatedString(text),
                        style = textStyle,
                        overflow = TextOverflow.Clip,
                        softWrap = true,
                        maxLines = maxLines,
                        constraints = Constraints(maxWidth = availableWidth),
                    )
                    val count = if (layout.lineCount == 0) 0 else layout.getLineEnd(layout.lineCount - 1, visibleEnd = false)
                    MeasuredTextSlice(count, layout.size.height)
                }
            }
        }
        val targetProgress = fragment?.let(content.anchors::get)?.let {
            content.progressAt(it.blockIndex, it.fractionInBlock)
        } ?: initialFraction
        val pagerState = rememberPagerState(
            initialPage = pages.pageForProgress(targetProgress),
            pageCount = pages::size,
        )
        val scope = rememberCoroutineScope()
        var restored by remember(content) { mutableStateOf(false) }

        LaunchedEffect(pages, jumpToken) {
            restored = false
            pagerState.scrollToPage(pages.pageForProgress(targetProgress))
            restored = true
            if (fragment != null) onFragmentConsumed()
        }
        LaunchedEffect(pagerState, pages, restored) {
            if (!restored) return@LaunchedEffect
            snapshotFlow { pagerState.settledPage }.distinctUntilChanged().collect { page ->
                pages.getOrNull(page)?.let { onProgress(it.startProgress) }
            }
        }
        LaunchedEffect(activeSentence, pages) {
            val sentence = activeSentence ?: return@LaunchedEffect
            val targetPage = pages.indexOfFirst { page ->
                page.items.filterIsInstance<ReaderPageItem.Text>().any { item ->
                    item.blockIndex == sentence.blockIndex && sentence.startOffset in item.startOffset until item.endOffset
                }
            }
            if (targetPage >= 0 && targetPage != pagerState.currentPage) pagerState.animateScrollToPage(targetPage)
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize().pointerInput(pages, hasPreviousChapter, hasNextChapter) {
                detectHorizontalReaderGestures(
                    currentPage = { pagerState.currentPage },
                    lastPage = pages.lastIndex,
                    onTap = { position ->
                        when {
                            position.x < size.width * 0.32f -> scope.launch {
                                onManualNavigation()
                                if (pagerState.currentPage > 0) pagerState.animateScrollToPage(pagerState.currentPage - 1)
                                else if (hasPreviousChapter) onPreviousChapter()
                            }
                            position.x > size.width * 0.68f -> scope.launch {
                                onManualNavigation()
                                if (pagerState.currentPage < pages.lastIndex) pagerState.animateScrollToPage(pagerState.currentPage + 1)
                                else if (hasNextChapter) onNextChapter()
                            }
                            else -> onToggleControls()
                        }
                    },
                    onSwipePastStart = { if (hasPreviousChapter) onPreviousChapter() },
                    onSwipePastEnd = { if (hasNextChapter) onNextChapter() },
                    onUserSwipe = onManualNavigation,
                )
            },
        ) { pageIndex ->
            Column(
                Modifier.fillMaxSize().padding(start = 22.dp, end = 22.dp, top = 84.dp, bottom = 92.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                pages[pageIndex].items.forEach { item ->
                    when (item) {
                        is ReaderPageItem.Text -> {
                            val activeRange = activeRangeInSlice(activeSentence, item)
                            Text(
                            text = highlightedText(item.text, activeRange, palette),
                            style = textStyle,
                            color = palette.foreground,
                            modifier = Modifier.height(with(density) { item.heightPx.toDp() })
                                .semantics { selected = activeRange != null },
                            )
                        }
                        is ReaderPageItem.Image -> ReaderImage(
                            image = item.block,
                            palette = palette,
                            height = with(density) { item.heightPx.toDp() },
                        )
                    }
                }
            }
        }
    }
}

private suspend fun PointerInputScope.detectHorizontalReaderGestures(
    currentPage: () -> Int,
    lastPage: Int,
    onTap: (Offset) -> Unit,
    onSwipePastStart: () -> Unit,
    onSwipePastEnd: () -> Unit,
    onUserSwipe: () -> Unit,
) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val startPosition = down.position
        val startPage = currentPage()
        var endPosition = startPosition
        var pressed = true
        do {
            val event = awaitPointerEvent(PointerEventPass.Final)
            val change = event.changes.firstOrNull { it.id == down.id } ?: break
            endPosition = change.position
            pressed = change.pressed
        } while (pressed)
        val delta = endPosition - startPosition
        val threshold = viewConfiguration.touchSlop * 3
        if (abs(delta.x) > viewConfiguration.touchSlop || abs(delta.y) > viewConfiguration.touchSlop) onUserSwipe()
        when {
            abs(delta.x) < viewConfiguration.touchSlop && abs(delta.y) < viewConfiguration.touchSlop -> onTap(endPosition)
            abs(delta.x) > threshold && abs(delta.x) > abs(delta.y) && delta.x > 0 && startPage == 0 -> onSwipePastStart()
            abs(delta.x) > threshold && abs(delta.x) > abs(delta.y) && delta.x < 0 && startPage == lastPage -> onSwipePastEnd()
        }
    }
}

@Composable
private fun ReaderBlockView(
    blockIndex: Int,
    block: ReaderBlock,
    textStyle: TextStyle,
    palette: ReaderPalette,
    activeSentence: TtsSentenceLocator?,
) {
    when (block) {
        is ReaderBlock.Text -> ReaderTextBlock(blockIndex, block.text, textStyle, palette, activeSentence)
        is ReaderBlock.Image -> ReaderImage(block, palette)
    }
}

@Composable
private fun ReaderTextBlock(
    blockIndex: Int,
    text: String,
    textStyle: TextStyle,
    palette: ReaderPalette,
    activeSentence: TtsSentenceLocator?,
) {
    val range = activeSentence
        ?.takeIf { it.blockIndex == blockIndex }
        ?.let { it.startOffset.coerceIn(0, text.length) until it.endOffset.coerceIn(0, text.length) }
        ?.takeUnless { it.isEmpty() }
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    var layoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
    LaunchedEffect(range, layoutResult) {
        val activeRange = range ?: return@LaunchedEffect
        val layout = layoutResult ?: return@LaunchedEffect
        val first = layout.getBoundingBox(activeRange.first)
        val last = layout.getBoundingBox(activeRange.last)
        bringIntoViewRequester.bringIntoView(
            Rect(0f, first.top, layout.size.width.toFloat(), last.bottom),
        )
    }
    Text(
        text = highlightedText(text, range, palette),
        style = textStyle,
        color = palette.foreground,
        onTextLayout = { layoutResult = it },
        modifier = Modifier.bringIntoViewRequester(bringIntoViewRequester)
            .semantics { selected = range != null },
    )
}

private fun activeRangeInSlice(sentence: TtsSentenceLocator?, item: ReaderPageItem.Text): IntRange? {
    if (sentence == null || sentence.blockIndex != item.blockIndex) return null
    val start = maxOf(sentence.startOffset, item.startOffset)
    val end = minOf(sentence.endOffset, item.endOffset)
    return if (start < end) (start - item.startOffset) until (end - item.startOffset) else null
}

private fun highlightedText(text: String, range: IntRange?, palette: ReaderPalette): AnnotatedString = buildAnnotatedString {
    append(text)
    if (range != null && !range.isEmpty()) {
        addStyle(
            SpanStyle(background = palette.foreground.copy(alpha = 0.18f)),
            range.first.coerceIn(0, text.length),
            (range.last + 1).coerceIn(0, text.length),
        )
    }
}

@Composable
private fun ReaderImage(image: ReaderBlock.Image, palette: ReaderPalette, height: Dp? = null) {
    val modifier = Modifier.fillMaxWidth().let { base ->
        when {
            height != null -> base.height(height)
            image.aspectRatio != null -> base.heightIn(max = 720.dp).aspectRatio(image.aspectRatio.coerceIn(0.15f, 8f))
            else -> base.heightIn(min = 120.dp, max = 480.dp)
        }
    }
    if (image.absolutePath == null) {
        ImagePlaceholder(image.contentDescription, palette, modifier)
        return
    }
    SubcomposeAsyncImage(
        model = File(image.absolutePath),
        contentDescription = image.contentDescription,
        contentScale = ContentScale.Fit,
        modifier = modifier,
        loading = { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = palette.muted) } },
        error = { ImagePlaceholder(image.contentDescription, palette, Modifier.fillMaxSize()) },
    )
}

@Composable
private fun ImagePlaceholder(label: String, palette: ReaderPalette, modifier: Modifier = Modifier) {
    Column(
        modifier.background(palette.placeholder).padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Default.BrokenImage, contentDescription = null, tint = palette.muted)
        Text(label, color = palette.muted, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
internal fun ChapterNavigation(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    palette: ReaderPalette,
    edge: ChapterPullEdge,
    pullProgress: Float,
    armed: Boolean,
) {
    val progress = pullProgress.coerceIn(0f, 1f)
    val scale by animateFloatAsState(
        targetValue = if (armed) 1.06f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "chapterNavigationScale",
    )
    val state = when {
        !enabled -> "没有$label"
        armed -> "松手切换$label"
        progress > 0f && edge == ChapterPullEdge.PREVIOUS -> "继续下拉切换$label"
        progress > 0f -> "继续上拉切换$label"
        else -> "点击切换$label"
    }
    val textColor = when {
        !enabled -> palette.placeholder
        else -> lerp(palette.muted, palette.foreground, progress)
    }
    Box(
        Modifier.fillMaxWidth()
            .testTag("chapter-navigation-${edge.name.lowercase()}")
            .semantics { stateDescription = state }
            .background(
                color = palette.foreground.copy(alpha = 0.16f * progress),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
            ),
        contentAlignment = Alignment.Center,
    ) {
        TextButton(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier.graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
        ) {
            Text(label, color = textColor, fontWeight = if (armed) FontWeight.SemiBold else FontWeight.Normal)
        }
    }
}

private val CHAPTER_PULL_THRESHOLD = 64.dp
private val CHAPTER_PULL_MAX_DISTANCE = 112.dp

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
private fun ReaderError(message: String, onRetry: () -> Unit, onBack: () -> Unit, palette: ReaderPalette) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("无法打开书籍", style = MaterialTheme.typography.headlineSmall, color = palette.foreground)
        Text(message, Modifier.padding(vertical = 12.dp), color = palette.muted)
        Row {
            TextButton(onClick = onBack) { Text("返回书架") }
            TextButton(onClick = onRetry) { Text("重试") }
        }
    }
}

@Composable
private fun readerTextStyle(settings: ReaderSettings, color: Color) = TextStyle(
    color = color,
    fontSize = settings.fontSizeSp.sp,
    lineHeight = (settings.fontSizeSp * settings.lineHeight).sp,
)

internal data class ReaderPalette(
    val background: Color,
    val foreground: Color,
    val muted: Color,
    val placeholder: Color,
)

@Composable
private fun readerPalette(theme: ReaderTheme): ReaderPalette {
    val darkSystem = androidx.compose.foundation.isSystemInDarkTheme()
    return when (theme) {
        ReaderTheme.LIGHT -> ReaderPalette(Color(0xFFFAF8F3), Color(0xFF25231F), Color(0xFF6D685E), Color(0xFFE8E3D9))
        ReaderTheme.DARK -> ReaderPalette(Color(0xFF171717), Color(0xFFE7E2D8), Color(0xFFAAA49A), Color(0xFF292826))
        ReaderTheme.SEPIA -> ReaderPalette(Color(0xFFF2E8CF), Color(0xFF43392A), Color(0xFF786A55), Color(0xFFE3D5B5))
        ReaderTheme.SYSTEM -> if (darkSystem) {
            ReaderPalette(Color(0xFF171717), Color(0xFFE7E2D8), Color(0xFFAAA49A), Color(0xFF292826))
        } else {
            ReaderPalette(Color(0xFFFAF8F3), Color(0xFF25231F), Color(0xFF6D685E), Color(0xFFE8E3D9))
        }
    }
}
