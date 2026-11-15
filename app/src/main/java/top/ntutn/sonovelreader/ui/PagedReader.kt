package top.ntutn.sonovelreader.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.distinctUntilChanged
import top.ntutn.sonovelreader.data.ReaderContent
import top.ntutn.sonovelreader.data.ReaderPageItem
import top.ntutn.sonovelreader.data.ReaderSettings
import top.ntutn.sonovelreader.tts.TtsSentenceLocator

@Composable
internal fun PagedReader(
    content: ReaderContent,
    settings: ReaderSettings,
    palette: ReaderPalette,
    chapterTitle: String,
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
    previousChapter: ReaderChapterContent? = null,
    nextChapter: ReaderChapterContent? = null,
    chapterKey: String = chapterTitle,
    navigationEnabled: Boolean = true,
    activeSentence: TtsSentenceLocator? = null,
    onManualNavigation: () -> Unit = {},
    onDisplayInfo: (ReaderDisplayInfo) -> Unit = {},
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val textMeasurer = rememberTextMeasurer()
        val indentStyle = readerTextStyle(settings, palette.foreground, applyIndent = true)
        val noIndentStyle = readerTextStyle(settings, palette.foreground, applyIndent = false)
        val padding = LocalReaderPadding.current
        val direction = LocalLayoutDirection.current
        val widthPx = with(density) { (maxWidth - padding.calculateStartPadding(direction) - padding.calculateEndPadding(direction)).roundToPx().coerceAtLeast(1) }
        val heightPx = with(density) { (maxHeight - padding.calculateTopPadding() - padding.calculateBottomPadding()).roundToPx().coerceAtLeast(1) }
        val spacingPx = with(density) { settings.paragraphSpacingDp.dp.roundToPx() }
        val lineHeightPx = with(density) { (settings.fontSizeSp * settings.lineHeight).sp.roundToPx().coerceAtLeast(1) }
        val pages = rememberChapterPages(content, chapterTitle, settings, widthPx, heightPx, spacingPx,
            lineHeightPx, indentStyle, noIndentStyle, textMeasurer)
        val previousPages = previousChapter?.let {
            rememberChapterPages(it.content, it.title, settings, widthPx, heightPx, spacingPx,
                lineHeightPx, indentStyle, noIndentStyle, textMeasurer)
        }.orEmpty()
        val nextPages = nextChapter?.let {
            rememberChapterPages(it.content, it.title, settings, widthPx, heightPx, spacingPx,
                lineHeightPx, indentStyle, noIndentStyle, textMeasurer)
        }.orEmpty()
        val window = remember(pages, previousPages, nextPages, chapterTitle, previousChapter, nextChapter) {
            chapterPageWindow(pages, chapterTitle, previousPages, previousChapter?.title, nextPages, nextChapter?.title)
        }
        val pageOffset = if (previousPages.isEmpty()) 0 else 1
        val targetProgress = fragment?.let(content.anchors::get)?.let {
            content.progressAt(it.blockIndex, it.fractionInBlock)
        } ?: initialFraction
        val pagerState = remember(chapterKey, content, pages, pageOffset, window.size, jumpToken) {
            PagerState(
                currentPage = pageOffset + pages.pageForProgress(targetProgress),
                pageCount = window::size,
            )
        }
        var restored by remember(chapterKey, content) { mutableStateOf(false) }

        LaunchedEffect(pagerState) {
            restored = false
            pagerState.scrollToPage(pageOffset + pages.pageForProgress(targetProgress))
            restored = true
            if (fragment != null) onFragmentConsumed()
        }
        LaunchedEffect(pagerState, window, restored, onDisplayInfo) {
            if (!restored) return@LaunchedEffect
            snapshotFlow { pagerState.settledPage to pagerState.isScrollInProgress }.distinctUntilChanged().collect { (page, scrolling) ->
                if (!scrolling) {
                    window.getOrNull(page)?.let {
                        onDisplayInfo(it.displayInfo())
                        when (it.chapterOffset) {
                            -1 -> onPreviousChapter()
                            1 -> onNextChapter()
                            else -> onProgress(it.page.startProgress)
                        }
                    }
                }
            }
        }
        LaunchedEffect(activeSentence, pages) {
            val sentence = activeSentence ?: return@LaunchedEffect
            val targetPage = pages.indexOfFirst { page ->
                page.items.filterIsInstance<ReaderPageItem.Text>().any { item ->
                    item.blockIndex == sentence.blockIndex && sentence.startOffset in item.startOffset until item.endOffset
                }
            }
            if (targetPage >= 0 && targetPage + pageOffset != pagerState.currentPage) pagerState.animateScrollToPage(targetPage + pageOffset)
        }

        val navigation = rememberPagedNavigation(pagerState, restored && navigationEnabled,
            onManualNavigation,
            { if (hasPreviousChapter && previousChapter == null) onPreviousChapter() },
            { if (hasNextChapter && nextChapter == null) onNextChapter() })

        HorizontalPager(
            state = pagerState,
            userScrollEnabled = navigationEnabled,
            modifier = Modifier.fillMaxSize().pointerInput(navigation) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    navigation.pointerDown = true
                    try {
                        do {
                            val event = awaitPointerEvent(PointerEventPass.Final)
                        } while (event.changes.any { it.pressed })
                    } finally {
                        navigation.pointerDown = false
                    }
                }
            }.pointerInput(pagerState, window, navigationEnabled, hasPreviousChapter, hasNextChapter) {
                detectHorizontalReaderGestures(
                    currentPage = { pagerState.currentPage },
                    lastPage = window.lastIndex,
                    onTap = { position ->
                        if (!navigationEnabled) return@detectHorizontalReaderGestures
                        when {
                            position.x < size.width * 0.32f -> navigation.turn(false, fromKey = false)
                            position.x > size.width * 0.68f -> navigation.turn(true, fromKey = false)
                            else -> onToggleControls()
                        }
                    },
                    onSwipePastStart = { if (navigationEnabled && hasPreviousChapter && previousChapter == null) onPreviousChapter() },
                    onSwipePastEnd = { if (navigationEnabled && hasNextChapter && nextChapter == null) onNextChapter() },
                    onUserSwipe = onManualNavigation,
                )
            },
        ) { pageIndex ->
            val entry = window[pageIndex]
            Box(Modifier.fillMaxSize()) {
            Column(
                Modifier.fillMaxSize().padding(padding),
                verticalArrangement = Arrangement.spacedBy(settings.paragraphSpacingDp.dp),
            ) {
                if (entry.title != null) {
                    Text(
                        text = entry.title,
                        style = readerChapterTitleStyle(settings, palette.foreground),
                        color = palette.foreground,
                    )
                }
                entry.page.items.forEach { item ->
                    when (item) {
                        is ReaderPageItem.Text -> {
                            val activeRange = activeRangeInSlice(activeSentence.takeIf { entry.chapterOffset == 0 }, item)
                            Text(
                                text = highlightedText(item.text, activeRange, palette),
                                style = if (item.startOffset == 0) indentStyle else noIndentStyle,
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
            ReaderPageInfo(entry)
            }
        }
    }
}
