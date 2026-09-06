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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
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
        val chapterTitleStyle = indentStyle.copy(
            fontSize = (settings.fontSizeSp * 1.3f).sp,
            fontWeight = FontWeight.Bold,
        )
        val titleReservedPx = remember(chapterTitle, widthPx, chapterTitleStyle, spacingPx) {
            val layout = textMeasurer.measure(
                text = AnnotatedString(chapterTitle),
                style = chapterTitleStyle,
                overflow = TextOverflow.Clip,
                softWrap = true,
                constraints = Constraints(maxWidth = widthPx),
            )
            // 标题高度 + 标题与正文之间的段落间距
            layout.size.height + if (layout.size.height > 0) spacingPx else 0
        }
        val pages = remember(content, widthPx, heightPx, spacingPx, indentStyle, noIndentStyle, textMeasurer, titleReservedPx) {
            paginateReaderContent(content, widthPx, heightPx, spacingPx, firstPageReservedHeightPx = titleReservedPx) { text, availableWidth, availableHeight, isStartOfBlock ->
                val maxLines = (availableHeight / lineHeightPx).coerceAtLeast(0)
                if (maxLines == 0) {
                    MeasuredTextSlice(0, 0)
                } else {
                    val layout = textMeasurer.measure(
                        text = AnnotatedString(text),
                        style = if (isStartOfBlock) indentStyle else noIndentStyle,
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
        val pagerState = remember(content, jumpToken) {
            PagerState(
                currentPage = pages.pageForProgress(targetProgress),
                pageCount = pages::size,
            )
        }
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
                verticalArrangement = Arrangement.spacedBy(settings.paragraphSpacingDp.dp),
            ) {
                if (pageIndex == 0) {
                    Text(
                        text = chapterTitle,
                        fontSize = (settings.fontSizeSp * 1.3f).sp,
                        fontWeight = FontWeight.Bold,
                        color = palette.foreground,
                    )
                }
                pages[pageIndex].items.forEach { item ->
                    when (item) {
                        is ReaderPageItem.Text -> {
                            val activeRange = activeRangeInSlice(activeSentence, item)
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
