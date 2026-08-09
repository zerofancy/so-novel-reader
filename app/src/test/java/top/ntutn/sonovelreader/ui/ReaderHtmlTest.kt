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

    @Test
    fun darkTheme_overridesBookDefinedHeadingColor() {
        val file = temporaryChapter("<h1 style=\"color: black\">Chapter title</h1>")
        val html = buildReaderHtml(
            file,
            ReaderSettings(theme = ReaderTheme.DARK),
            darkSystem = false,
        )

        assertTrue(html.contains("h1, h2, h3, h4, h5, h6"))
        assertTrue(html.contains("color: #E7E2D8 !important"))
        file.delete()
    }

    private fun temporaryChapter(body: String = "<p>Hello</p>"): File =
        File.createTempFile("reader", ".xhtml").apply {
            writeText("<html><head><title>Test</title></head><body>$body</body></html>")
        }
}
