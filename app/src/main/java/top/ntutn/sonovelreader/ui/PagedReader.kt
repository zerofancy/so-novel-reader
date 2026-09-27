package top.ntutn.sonovelreader.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
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
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val textMeasurer = rememberTextMeasurer()
        val indentStyle = readerTextStyle(settings, palette.foreground, applyIndent = true)
        val noIndentStyle = readerTextStyle(settings, palette.foreground, applyIndent = false)
        val widthPx = with(density) { (maxWidth - 44.dp).roundToPx().coerceAtLeast(1) }
        val heightPx = with(density) { (maxHeight - 176.dp).roundToPx().coerceAtLeast(1) }
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
        val pagerState = remember(chapterKey, content, pages, jumpToken) {
            PagerState(
                currentPage = pageOffset + pages.pageForProgress(targetProgress),
                pageCount = window::size,
            )
        }
        val scope = rememberCoroutineScope()
        var restored by remember(chapterKey, content) { mutableStateOf(false) }

        LaunchedEffect(pagerState) {
            restored = false
            pagerState.scrollToPage(pageOffset + pages.pageForProgress(targetProgress))
            restored = true
            if (fragment != null) onFragmentConsumed()
        }
        LaunchedEffect(pagerState, pages, restored) {
            if (!restored) return@LaunchedEffect
            snapshotFlow { pagerState.settledPage to pagerState.isScrollInProgress }.distinctUntilChanged().collect { (page, scrolling) ->
                if (!scrolling) {
                    window.getOrNull(page)?.let {
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

        HorizontalPager(
            state = pagerState,
            userScrollEnabled = navigationEnabled,
            modifier = Modifier.fillMaxSize().pointerInput(pagerState, window, navigationEnabled, hasPreviousChapter, hasNextChapter) {
                detectHorizontalReaderGestures(
                    currentPage = { pagerState.currentPage },
                    lastPage = window.lastIndex,
                    onTap = { position ->
                        if (!navigationEnabled) return@detectHorizontalReaderGestures
                        when {
                            position.x < size.width * 0.32f -> scope.launch {
                                onManualNavigation()
                                if (pagerState.currentPage > 0) pagerState.animateScrollToPage(pagerState.currentPage - 1)
                                else if (navigationEnabled && hasPreviousChapter && previousChapter == null) onPreviousChapter()
                            }
                            position.x > size.width * 0.68f -> scope.launch {
                                onManualNavigation()
                                if (pagerState.currentPage < window.lastIndex) pagerState.animateScrollToPage(pagerState.currentPage + 1)
                                else if (navigationEnabled && hasNextChapter && nextChapter == null) onNextChapter()
                            }
                            else -> onToggleControls()
                        }
                    },
                    onSwipePastStart = { if (navigationEnabled && hasPreviousChapter && previousChapter == null) onPreviousChapter() },
                    onSwipePastEnd = { if (navigationEnabled && hasNextChapter && nextChapter == null) onNextChapter() },
                    onUserSwipe = onManualNavigation,
                )
            },
        ) { pageIndex ->
            Column(
                Modifier.fillMaxSize().padding(start = 22.dp, end = 22.dp, top = 84.dp, bottom = 92.dp),
                verticalArrangement = Arrangement.spacedBy(settings.paragraphSpacingDp.dp),
            ) {
                val entry = window[pageIndex]
                if (entry.title != null) {
                    Text(
                        text = entry.title,
                        fontSize = (settings.fontSizeSp * 1.3f).sp,
                        fontWeight = FontWeight.Bold,
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
        }
    }
}
