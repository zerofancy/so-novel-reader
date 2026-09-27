package top.ntutn.sonovelreader.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ptq.mpga.ptqbookpageview.widget.PTQBookPageView
import ptq.mpga.ptqbookpageview.widget.PTQBookPageViewState
import ptq.mpga.ptqbookpageview.widget.rememberPTQBookPageViewConfig
import top.ntutn.sonovelreader.data.ReaderContent
import top.ntutn.sonovelreader.data.ReaderPageItem
import top.ntutn.sonovelreader.data.ReaderSettings
import top.ntutn.sonovelreader.tts.TtsSentenceLocator

@Composable
internal fun FlipReader(
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
        val initialPage = pageOffset + pages.pageForProgress(targetProgress)
        var currentPage by remember(chapterKey, content, pages, jumpToken) { mutableIntStateOf(initialPage) }
        var ptqStateHolder by remember(chapterKey, content, pages, jumpToken) {
            mutableStateOf(
                PTQBookPageViewState(
                    pageCount = window.size,
                    currentPage = initialPage,
                )
            )
        }

        LaunchedEffect(chapterKey, pages, jumpToken) {
            currentPage = initialPage
            if (fragment != null) onFragmentConsumed()
        }
        LaunchedEffect(chapterKey, currentPage) {
            window.getOrNull(currentPage)?.takeIf { it.chapterOffset == 0 }?.let { onProgress(it.page.startProgress) }
        }
        LaunchedEffect(activeSentence, pages) {
            val sentence = activeSentence ?: return@LaunchedEffect
            val targetPage = pages.indexOfFirst { page ->
                page.items.filterIsInstance<ReaderPageItem.Text>().any { item ->
                    item.blockIndex == sentence.blockIndex &&
                        sentence.startOffset in item.startOffset until item.endOffset
                }
            }
            if (targetPage >= 0 && targetPage + pageOffset != currentPage) {
                currentPage = targetPage + pageOffset
                ptqStateHolder = ptqStateHolder.copy(
                    pageCount = window.size,
                    currentPage = targetPage + pageOffset,
                )
            }
        }

        val config by rememberPTQBookPageViewConfig(
            pageColor = palette.background,
            disabled = false,
        )

        key(chapterKey, pages, jumpToken) {
            PTQBookPageView(
                modifier = Modifier.fillMaxSize(),
                state = ptqStateHolder,
                config = config.copy(pageColor = palette.background, disabled = !navigationEnabled),
            ) {
                onTurnPageRequest { _, isNextOrPrevious, success ->
                    onManualNavigation()
                    if (!success) {
                        if (isNextOrPrevious && hasNextChapter && nextChapter == null) onNextChapter()
                        if (!isNextOrPrevious && hasPreviousChapter && previousChapter == null) onPreviousChapter()
                        return@onTurnPageRequest
                    }
                    val targetPage = currentPage + if (isNextOrPrevious) 1 else -1
                    val entry = window.getOrNull(targetPage) ?: return@onTurnPageRequest
                    currentPage = targetPage
                    ptqStateHolder = ptqStateHolder.copy(currentPage = targetPage)
                    // PTQ calls this after the curl animation has finished.
                    when (entry.chapterOffset) {
                        -1 -> onPreviousChapter()
                        1 -> onNextChapter()
                    }
                }
                tapBehavior { leftUp, rightDown, touchPoint ->
                    val w = rightDown.x - leftUp.x
                    when {
                        touchPoint.x < w * 0.32f -> false
                        touchPoint.x > w * 0.68f -> true
                        else -> {
                            onToggleControls()
                            null
                        }
                    }
                }
                contents { page, refresh ->
                    Box(
                        Modifier.fillMaxSize().background(palette.background)
                    ) {
                        FlipPageContent(
                            entry = window.getOrNull(page) ?: window[initialPage],
                            settings = settings,
                            palette = palette,
                            indentStyle = indentStyle,
                            noIndentStyle = noIndentStyle,
                            density = density,
                            activeSentence = activeSentence,
                        )
                    }
                    refresh()
                }
            }
        }
    }
}

@Composable
private fun FlipPageContent(
    entry: ChapterPage,
    settings: ReaderSettings,
    palette: ReaderPalette,
    indentStyle: TextStyle,
    noIndentStyle: TextStyle,
    density: Density,
    activeSentence: TtsSentenceLocator?,
) {
    Column(
        Modifier.fillMaxSize().padding(start = 22.dp, end = 22.dp, top = 84.dp, bottom = 92.dp),
        verticalArrangement = Arrangement.spacedBy(settings.paragraphSpacingDp.dp),
    ) {
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
