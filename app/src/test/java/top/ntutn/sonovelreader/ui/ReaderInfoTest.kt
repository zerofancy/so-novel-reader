package top.ntutn.sonovelreader.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import top.ntutn.sonovelreader.data.ReaderPage

class ReaderInfoTest {
    @Test
    fun previewPagesUseSourceChapterPageNumbers() {
        fun pages(count: Int) = List(count) { ReaderPage(emptyList(), it.toFloat() / count) }
        val window = chapterPageWindow(pages(3), "当前", pages(7), "前章", pages(5), "后章")
        assertEquals(ReaderDisplayInfo(-1, 7, 7), window.first().displayInfo())
        assertEquals(ReaderDisplayInfo(0, 1, 3), window[1].displayInfo())
        assertEquals(ReaderDisplayInfo(0, 3, 3), window[3].displayInfo())
        assertEquals(ReaderDisplayInfo(1, 1, 5), window.last().displayInfo())
        assertEquals("前章", window.first().chapterTitle)
        assertEquals("当前", window[2].chapterTitle)
        assertEquals("后章", window.last().chapterTitle)
    }

    @Test
    fun batteryHandlesUnknownNonPercentScalesAndOverflow() {
        assertNull(batteryPercent(-1, 100))
        assertNull(batteryPercent(10, 0))
        assertEquals(76, batteryPercent(152, 200))
        assertEquals(0, batteryPercent(0, 100))
        assertEquals(100, batteryPercent(Int.MAX_VALUE, Int.MAX_VALUE))
        assertEquals(100, batteryPercent(101, 100))
    }

    @Test
    fun scrollProgressFloorsAndOverridesOnlyAtEnd() {
        assertEquals(42, readerPercent(0.429f, false))
        assertEquals(100, readerPercent(0.1f, true))
        assertEquals(0, readerPercent(-1f, false))
        assertEquals(100, readerPercent(2f, false))
        assertNull(readerPercent(Float.NaN, false))
    }
}
