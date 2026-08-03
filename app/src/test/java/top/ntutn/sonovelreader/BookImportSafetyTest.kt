package top.ntutn.sonovelreader

import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import top.ntutn.sonovelreader.data.BookRepository
import top.ntutn.sonovelreader.data.ImportBatchResult
import top.ntutn.sonovelreader.data.ImportItemResult
import top.ntutn.sonovelreader.data.sanitizeEpubMarkup

class BookImportSafetyTest {
    @Test
    fun safeRelativePath_acceptsNormalEpubPaths() {
        assertEquals("OEBPS/Text/chapter.xhtml", BookRepository.safeRelativePath("OEBPS/Text/chapter.xhtml#part"))
        assertEquals("chapter.xhtml", BookRepository.safeRelativePath("./chapter.xhtml"))
    }

    @Test
    fun safeRelativePath_rejectsTraversalAndAbsolutePaths() {
        assertNull(BookRepository.safeRelativePath("../secret.txt"))
        assertNull(BookRepository.safeRelativePath("/absolute/chapter.xhtml"))
        assertNull(BookRepository.safeRelativePath("C:\\secret.txt"))
        assertNull(BookRepository.safeRelativePath("OEBPS//chapter.xhtml"))
    }

    @Test
    fun sanitizer_removesExecutableAndExternalContent() {
        val source = """
            <html><body onload="steal()"><script>steal()</script>
            <iframe src="https://example.com"></iframe>
            <a href="javascript:steal()">bad</a><a href="chapter2.xhtml">good</a>
            </body></html>
        """.trimIndent().toByteArray()
        val document = Jsoup.parse(String(sanitizeEpubMarkup(source, "UTF-8", false)))
        assertTrue(document.select("script, iframe").isEmpty())
        assertFalse(document.body().hasAttr("onload"))
        assertFalse(document.selectFirst("a:contains(bad)")!!.hasAttr("href"))
        assertEquals("chapter2.xhtml", document.selectFirst("a:contains(good)")!!.attr("href"))
    }

    @Test
    fun batchResult_countsImportedExistingAndFailedItems() {
        val result = ImportBatchResult(
            listOf(
                ImportItemResult.Imported("a.epub", "a"),
                ImportItemResult.Existing("b.epub", "b"),
                ImportItemResult.Failed("c.epub", "损坏"),
            ),
        )
        assertEquals(1, result.importedCount)
        assertEquals(1, result.existingCount)
        assertEquals(1, result.failedCount)
        assertEquals(listOf("a", "b"), result.successfulBookIds)
    }
}
