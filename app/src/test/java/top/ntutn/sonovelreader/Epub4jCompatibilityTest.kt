package top.ntutn.sonovelreader

import io.documentnode.epub4j.domain.Author
import io.documentnode.epub4j.domain.Book
import io.documentnode.epub4j.domain.Resource
import io.documentnode.epub4j.epub.EpubReader
import io.documentnode.epub4j.epub.EpubWriter
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class Epub4jCompatibilityTest {
    @Test
    fun generatedChineseNovel_canBeReadBack() {
        val output = File("build/test-artifacts/sample.epub").apply { parentFile?.mkdirs() }
        val book = Book().apply {
            metadata.addTitle("测试小说")
            metadata.addAuthor(Author("测试作者"))
            addSection(
                "第一章 初见",
                Resource(
                    chapter("第一章 初见", "这是第一章正文。", "part-one").toByteArray(),
                    "Text/chapter1.xhtml",
                ),
            )
            addSection(
                "第二章 远行",
                Resource(
                    chapter("第二章 远行", "这是第二章正文。", "part-two").toByteArray(),
                    "Text/chapter2.xhtml",
                ),
            )
        }
        FileOutputStream(output).use { EpubWriter().write(book, it) }

        val restored = FileInputStream(output).use { EpubReader().readEpub(it) }
        assertEquals("测试小说", restored.metadata.firstTitle)
        assertEquals(2, restored.spine.size())
        assertFalse(restored.tableOfContents.tocReferences.isEmpty())
    }

    private fun chapter(title: String, body: String, id: String) = """
        <?xml version="1.0" encoding="UTF-8"?>
        <html xmlns="http://www.w3.org/1999/xhtml">
          <head><title>$title</title></head>
          <body><h1 id="$id">$title</h1>${(1..40).joinToString("") { "<p>$body 第 $it 段。</p>" }}</body>
        </html>
    """.trimIndent()
}
