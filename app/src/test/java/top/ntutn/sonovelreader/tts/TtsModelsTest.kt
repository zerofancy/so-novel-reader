package top.ntutn.sonovelreader.tts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import top.ntutn.sonovelreader.data.EMPTY_CHAPTER_TEXT
import top.ntutn.sonovelreader.data.ReaderBlock
import top.ntutn.sonovelreader.data.ReaderContent

class TtsModelsTest {
    @Test
    fun segmentsChineseAndEnglishSentencesWithOriginalOffsets() {
        val source = "第一句。第二句！ Hello world. Last one?"
        val sentences = segmentChapter(ReaderContent(listOf(ReaderBlock.Text(source))), 2)

        assertEquals(listOf("第一句。", "第二句！", "Hello world.", "Last one?"), sentences.map(TtsSentence::text))
        sentences.forEach { sentence ->
            assertEquals(sentence.text, source.substring(sentence.locator.startOffset, sentence.locator.endOffset))
            assertEquals(2, sentence.locator.chapterIndex)
        }
    }

    @Test
    fun skipsImagesWhitespaceAndEmptyChapterPlaceholder() {
        val content = ReaderContent(
            listOf(
                ReaderBlock.Image(null, "image", null),
                ReaderBlock.Text("   "),
                ReaderBlock.Text(EMPTY_CHAPTER_TEXT),
            ),
        )

        assertTrue(segmentChapter(content, 0).isEmpty())
    }

    @Test
    fun splitsLongSentencesWithoutBreakingSurrogatePairs() {
        val source = "开始" + "🚀".repeat(20) + "，" + "结束".repeat(20)
        val sentences = segmentChapter(ReaderContent(listOf(ReaderBlock.Text(source))), 0, maxInputLength = 18)

        assertTrue(sentences.all { it.text.length <= 18 })
        assertEquals(source, sentences.joinToString("") { it.text })
    }

    @Test
    fun locatesSentenceContainingOrFollowingSavedProgress() {
        val source = "第一句。第二句。第三句。"
        val content = ReaderContent(listOf(ReaderBlock.Text(source)))
        val sentences = segmentChapter(content, 0)

        assertEquals(0, sentenceIndexForProgress(content, sentences, 0f))
        assertEquals(1, sentenceIndexForProgress(content, sentences, 0.4f))
        assertEquals(sentences.size, sentenceIndexForProgress(content, sentences, 1f))
    }

    @Test
    fun queueBoundaryAdvancesAcrossEmptyChaptersAndCompletesAtBookEnd() {
        assertEquals(TtsQueueBoundary.SPEAK, ttsQueueBoundary(0, 2, 0, 2))
        assertEquals(TtsQueueBoundary.NEXT_CHAPTER, ttsQueueBoundary(0, 2, 2, 2))
        assertEquals(TtsQueueBoundary.NEXT_CHAPTER, ttsQueueBoundary(1, 2, 0, 0))
        assertEquals(TtsQueueBoundary.COMPLETE, ttsQueueBoundary(2, 2, 0, 0))
    }
}
