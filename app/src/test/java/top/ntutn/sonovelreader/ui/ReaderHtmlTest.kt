package top.ntutn.sonovelreader.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import top.ntutn.sonovelreader.data.ReaderSettings
import top.ntutn.sonovelreader.data.ReaderTheme
import top.ntutn.sonovelreader.data.ReadingMode

class ReaderHtmlTest {
    @Test
    fun pagedMode_injectsDiscreteViewportAndSelectedTypography() {
        val file = temporaryChapter()
        val html = buildReaderHtml(
            file,
            ReaderSettings(ReadingMode.PAGED, 24, 1.9f, ReaderTheme.SEPIA, false),
            darkSystem = false,
        )
        assertTrue(html.contains("scroll-behavior: smooth"))
        assertTrue(html.contains("font-size: 24px"))
        assertTrue(html.contains("#F2E8CF"))
        assertFalse(html.contains("sonovel://next"))
        file.delete()
    }

    @Test
    fun scrollMode_addsChapterNavigationAndClampsProgress() {
        val file = temporaryChapter()
        val html = buildReaderHtml(file, ReaderSettings(), darkSystem = false)
        assertTrue(html.contains("sonovel://previous"))
        assertTrue(html.contains("sonovel://next"))
        assertTrue(restorePositionScript(ReadingMode.SCROLL, 2f, null).contains("1.0"))
        file.delete()
    }

    private fun temporaryChapter(): File = File.createTempFile("reader", ".xhtml").apply {
        writeText("<html><head><title>Test</title></head><body><p>Hello</p></body></html>")
    }
}
