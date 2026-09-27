package top.ntutn.sonovelreader.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import top.ntutn.sonovelreader.data.ReaderPage

class ReaderChapterPagesTest {
    @Test
    fun windowIncludesPreviousLastPageAndNextFirstPage() {
        val previous = listOf(ReaderPage(emptyList(), 0f), ReaderPage(emptyList(), 0.7f))
        val current = listOf(ReaderPage(emptyList(), 0f), ReaderPage(emptyList(), 0.5f))
        val next = listOf(ReaderPage(emptyList(), 0f), ReaderPage(emptyList(), 0.6f))
        val window = chapterPageWindow(current, "当前章", previous, "上一章", next, "下一章")

        assertEquals(listOf(-1, 0, 0, 1), window.map { it.chapterOffset })
        assertSame(previous.last(), window.first().page)
        assertSame(next.first(), window.last().page)
        assertEquals(listOf(null, "当前章", null, "下一章"), window.map { it.title })
    }

    @Test
    fun singlePagePreviousChapterRetainsItsTitle() {
        val page = ReaderPage(emptyList(), 0f)
        val window = chapterPageWindow(listOf(page), "当前章", listOf(page), "上一章", emptyList(), null)
        assertEquals("上一章", window.first().title)
        assertEquals(listOf(-1, 0), window.map { it.chapterOffset })
    }

    @Test
    fun bookBoundariesDoNotIntroduceBlankPages() {
        val page = ReaderPage(emptyList(), 0f)
        val window = chapterPageWindow(listOf(page), "唯一章节", emptyList(), null, emptyList(), null)
        assertEquals(1, window.size)
        assertEquals(0, window.single().chapterOffset)
        assertSame(page, window.single().page)
    }
}
