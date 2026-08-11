package top.ntutn.sonovelreader.ui

import kotlin.math.ceil
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import top.ntutn.sonovelreader.data.ReaderBlock
import top.ntutn.sonovelreader.data.ReaderContent
import top.ntutn.sonovelreader.data.ReaderPageItem

class ReaderPaginationTest {
    private val layouter = ReaderTextLayouter { text, width, height ->
        val charactersPerLine = (width / 10).coerceAtLeast(1)
        val lines = (height / 20).coerceAtLeast(0)
        val count = (charactersPerLine * lines).coerceAtMost(text.length)
        val usedLines = if (count == 0) 0 else ceil(count.toDouble() / charactersPerLine).toInt()
        MeasuredTextSlice(count, usedLines * 20)
    }

    @Test
    fun longTextSplitsAcrossMeasuredPagesWithoutLosingCharacters() {
        val source = "x".repeat(105)
        val pages = paginateReaderContent(
            ReaderContent(listOf(ReaderBlock.Text(source))),
            widthPx = 100,
            heightPx = 100,
            spacingPx = 10,
            textLayouter = layouter,
        )

        val restored = pages.flatMap { it.items }.filterIsInstance<ReaderPageItem.Text>().joinToString("") { it.text }
        assertEquals(source, restored)
        assertEquals(3, pages.size)
        assertTrue(pages.zipWithNext().all { (first, second) -> first.startProgress < second.startProgress })
    }

    @Test
    fun imageMovesToNextPageAndOversizedImageFitsViewport() {
        val image = ReaderBlock.Image("/tmp/image.png", "image", aspectRatio = 0.2f)
        val content = ReaderContent(listOf(ReaderBlock.Text("x".repeat(40)), image))

        val pages = paginateReaderContent(content, 100, 100, 10, layouter)

        assertEquals(2, pages.size)
        val imageItem = pages.last().items.single() as ReaderPageItem.Image
        assertEquals(100, imageItem.heightPx)
    }

    @Test
    fun progressSelectsNearestPrecedingPage() {
        val pages = paginateReaderContent(
            ReaderContent(listOf(ReaderBlock.Text("x".repeat(100)))),
            100,
            100,
            0,
            layouter,
        )

        assertEquals(0, pages.pageForProgress(0f))
        assertEquals(1, pages.pageForProgress(0.6f))
        assertEquals(pages.lastIndex, pages.pageForProgress(1f))
    }
}
