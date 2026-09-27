package top.ntutn.sonovelreader.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.sp
import top.ntutn.sonovelreader.data.ReaderContent
import top.ntutn.sonovelreader.data.ReaderPage
import top.ntutn.sonovelreader.data.ReaderSettings

/** 已加载的相邻章节，供翻页组件提前绘制边界页。 */
data class ReaderChapterContent(val title: String, val content: ReaderContent)

internal data class ChapterPage(
    val page: ReaderPage,
    val title: String?,
    val chapterOffset: Int,
)

internal fun chapterPageWindow(
    pages: List<ReaderPage>,
    title: String,
    previous: List<ReaderPage>,
    previousTitle: String?,
    next: List<ReaderPage>,
    nextTitle: String?,
): List<ChapterPage> = buildList {
    previous.lastOrNull()?.let { add(ChapterPage(it, previousTitle.takeIf { previous.size == 1 }, -1)) }
    pages.forEachIndexed { index, page -> add(ChapterPage(page, title.takeIf { index == 0 }, 0)) }
    next.firstOrNull()?.let { add(ChapterPage(it, nextTitle, 1)) }
}

@Composable
internal fun rememberChapterPages(
    content: ReaderContent,
    chapterTitle: String,
    settings: ReaderSettings,
    widthPx: Int,
    heightPx: Int,
    spacingPx: Int,
    lineHeightPx: Int,
    indentStyle: TextStyle,
    noIndentStyle: TextStyle,
    textMeasurer: TextMeasurer,
): List<ReaderPage> {
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
    return remember(content, widthPx, heightPx, spacingPx, indentStyle, noIndentStyle, textMeasurer, titleReservedPx) {
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
}
