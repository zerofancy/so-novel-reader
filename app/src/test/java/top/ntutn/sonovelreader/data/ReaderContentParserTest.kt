package top.ntutn.sonovelreader.data

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderContentParserTest {
    @Test
    fun parsesTextImagesAndAnchorsInDocumentOrder() {
        val root = Files.createTempDirectory("reader-content").toFile()
        val textDir = File(root, "Text").apply { mkdirs() }
        val image = File(root, "Images/cover.png").apply { parentFile?.mkdirs(); writeBytes(byteArrayOf(1)) }
        val chapter = File(textDir, "chapter.xhtml").apply {
            writeText(
                """
                <html><body>
                  <p id="intro">第一<b>章</b><br/>正文</p>
                  <img src="../Images/cover.png" alt="插图" width="600" height="300"/>
                  <div>结尾</div>
                </body></html>
                """.trimIndent(),
            )
        }

        val content = parseReaderContent(chapter, root) { null }

        assertEquals(3, content.blocks.size)
        assertEquals("第一章\n正文", (content.blocks[0] as ReaderBlock.Text).text)
        val parsedImage = content.blocks[1] as ReaderBlock.Image
        assertEquals(image.canonicalPath, parsedImage.absolutePath)
        assertEquals("插图", parsedImage.contentDescription)
        assertEquals(2f, parsedImage.aspectRatio)
        assertEquals("结尾", (content.blocks[2] as ReaderBlock.Text).text)
        assertEquals(0, content.anchors.getValue("intro").blockIndex)
    }

    @Test
    fun unsafeRemoteMissingAndUnsupportedImagesBecomePlaceholders() {
        val root = Files.createTempDirectory("reader-content").toFile()
        val chapter = File(root, "chapter.xhtml").apply {
            writeText(
                """
                <html><body>
                  <img src="../../outside.png" alt="越界"/>
                  <img src="https://example.com/a.png" alt="远程"/>
                  <img src="missing.png" alt="缺失"/>
                  <img src="unsupported.bmp" alt="不支持"/>
                </body></html>
                """.trimIndent(),
            )
        }

        val images = parseReaderContent(chapter, root) { null }.blocks.filterIsInstance<ReaderBlock.Image>()

        assertEquals(4, images.size)
        assertTrue(images.all { it.absolutePath == null })
    }

    @Test
    fun readsSvgViewBoxAspectRatio() {
        val root = Files.createTempDirectory("reader-content").toFile()
        File(root, "wide.svg").writeText("<svg viewBox=\"0 0 800 200\"></svg>")
        val chapter = File(root, "chapter.xhtml").apply {
            writeText("<html><body><img src=\"wide.svg\"/></body></html>")
        }

        val image = parseReaderContent(chapter, root) { null }.blocks.single() as ReaderBlock.Image

        assertEquals(4f, image.aspectRatio)
    }

    @Test
    fun progressMapsBackToContentPosition() {
        val content = ReaderContent(listOf(ReaderBlock.Text("a".repeat(100)), ReaderBlock.Text("b".repeat(100))))

        val progress = content.progressAt(1, 0.5f)
        val restored = content.positionAt(progress)

        assertEquals(0.75f, progress)
        assertEquals(1, restored.blockIndex)
        assertEquals(0.5f, restored.fractionInBlock)
        assertNull(content.anchors["missing"])
    }
}
