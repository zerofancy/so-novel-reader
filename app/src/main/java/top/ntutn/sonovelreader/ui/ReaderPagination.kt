package top.ntutn.sonovelreader.ui

import kotlin.math.roundToInt
import top.ntutn.sonovelreader.data.ReaderBlock
import top.ntutn.sonovelreader.data.ReaderContent
import top.ntutn.sonovelreader.data.ReaderPage
import top.ntutn.sonovelreader.data.ReaderPageItem

internal data class MeasuredTextSlice(val characterCount: Int, val heightPx: Int)

internal fun interface ReaderTextLayouter {
    fun measure(text: String, widthPx: Int, maxHeightPx: Int, isStartOfBlock: Boolean): MeasuredTextSlice
}

internal fun paginateReaderContent(
    content: ReaderContent,
    widthPx: Int,
    heightPx: Int,
    spacingPx: Int,
    firstPageReservedHeightPx: Int = 0,
    textLayouter: ReaderTextLayouter,
): List<ReaderPage> {
    if (content.blocks.isEmpty() || widthPx <= 0 || heightPx <= 0) {
        return listOf(ReaderPage(emptyList(), 0f))
    }
    val pages = mutableListOf<ReaderPage>()
    val items = mutableListOf<ReaderPageItem>()
    var usedHeight = firstPageReservedHeightPx.coerceIn(0, heightPx)

    fun finishPage() {
        if (items.isEmpty()) return
        val first = items.first()
        val fraction = when (first) {
            is ReaderPageItem.Text -> {
                val block = content.blocks[first.blockIndex] as ReaderBlock.Text
                first.startOffset.toFloat() / block.text.length.coerceAtLeast(1)
            }
            is ReaderPageItem.Image -> 0f
        }
        pages += ReaderPage(items.toList(), content.progressAt(first.blockIndex, fraction))
        items.clear()
        usedHeight = 0
    }

    content.blocks.forEachIndexed { blockIndex, block ->
        when (block) {
            is ReaderBlock.Text -> {
                var offset = 0
                while (offset < block.text.length) {
                    val gap = if (items.isEmpty()) 0 else spacingPx
                    var remaining = heightPx - usedHeight - gap
                    if (remaining <= 0) {
                        finishPage()
                        remaining = heightPx
                    }
                    val isStartOfBlock = offset == 0
                    var measured = textLayouter.measure(block.text.substring(offset), widthPx, remaining, isStartOfBlock)
                    if (measured.characterCount <= 0 && items.isNotEmpty()) {
                        finishPage()
                        measured = textLayouter.measure(block.text.substring(offset), widthPx, heightPx, isStartOfBlock)
                    }
                    val consumed = measured.characterCount.coerceIn(1, block.text.length - offset)
                    val measuredHeight = measured.heightPx.coerceIn(1, heightPx)
                    if (items.isNotEmpty()) usedHeight += spacingPx
                    items += ReaderPageItem.Text(
                        blockIndex = blockIndex,
                        text = block.text.substring(offset, offset + consumed),
                        startOffset = offset,
                        endOffset = offset + consumed,
                        heightPx = measuredHeight,
                    )
                    usedHeight += measuredHeight
                    offset += consumed
                    if (offset < block.text.length) finishPage()
                }
            }
            is ReaderBlock.Image -> {
                val naturalHeight = (widthPx / (block.aspectRatio ?: DEFAULT_IMAGE_ASPECT_RATIO))
                    .roundToInt()
                    .coerceAtLeast(1)
                val imageHeight = naturalHeight.coerceAtMost(heightPx)
                val gap = if (items.isEmpty()) 0 else spacingPx
                if (items.isNotEmpty() && usedHeight + gap + imageHeight > heightPx) finishPage()
                if (items.isNotEmpty()) usedHeight += spacingPx
                items += ReaderPageItem.Image(blockIndex, block, imageHeight)
                usedHeight += imageHeight
                if (usedHeight >= heightPx) finishPage()
            }
        }
    }
    finishPage()
    return pages.ifEmpty { listOf(ReaderPage(emptyList(), 0f)) }
}

internal fun List<ReaderPage>.pageForProgress(progress: Float): Int {
    if (isEmpty()) return 0
    val target = progress.coerceIn(0f, 1f)
    return indexOfLast { it.startProgress <= target }.coerceAtLeast(0)
}

private const val DEFAULT_IMAGE_ASPECT_RATIO = 4f / 3f
