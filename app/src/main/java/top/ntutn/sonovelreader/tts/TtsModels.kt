package top.ntutn.sonovelreader.tts

import android.graphics.Bitmap
import java.text.BreakIterator
import java.util.Locale
import top.ntutn.sonovelreader.data.EMPTY_CHAPTER_TEXT
import top.ntutn.sonovelreader.data.ReaderBlock
import top.ntutn.sonovelreader.data.ReaderContent

enum class TtsPlaybackStatus { IDLE, PREPARING, PLAYING, PAUSED, ERROR, COMPLETED }

data class TtsSentenceLocator(
    val chapterIndex: Int,
    val blockIndex: Int,
    val startOffset: Int,
    val endOffset: Int,
)

data class TtsSentence(
    val locator: TtsSentenceLocator,
    val text: String,
)

data class TtsPlaybackState(
    val status: TtsPlaybackStatus = TtsPlaybackStatus.IDLE,
    val bookId: String? = null,
    val bookTitle: String? = null,
    val chapterTitle: String? = null,
    val activeSentence: TtsSentence? = null,
    val error: String? = null,
    val coverBitmap: Bitmap? = null,
)

internal enum class TtsQueueBoundary { SPEAK, NEXT_CHAPTER, COMPLETE }

internal fun ttsQueueBoundary(
    chapterIndex: Int,
    lastChapterIndex: Int,
    sentenceIndex: Int,
    sentenceCount: Int,
): TtsQueueBoundary = when {
    sentenceIndex in 0 until sentenceCount -> TtsQueueBoundary.SPEAK
    chapterIndex < lastChapterIndex -> TtsQueueBoundary.NEXT_CHAPTER
    else -> TtsQueueBoundary.COMPLETE
}

internal fun segmentChapter(
    content: ReaderContent,
    chapterIndex: Int,
    maxInputLength: Int = DEFAULT_MAX_SPEECH_INPUT_LENGTH,
): List<TtsSentence> = buildList {
    content.blocks.forEachIndexed { blockIndex, block ->
        if (block !is ReaderBlock.Text || block.text == EMPTY_CHAPTER_TEXT) return@forEachIndexed
        val locale = if (block.text.codePoints().anyMatch { Character.UnicodeScript.of(it) == Character.UnicodeScript.HAN }) {
            Locale.SIMPLIFIED_CHINESE
        } else {
            Locale.getDefault()
        }
        val iterator = BreakIterator.getSentenceInstance(locale).apply { setText(block.text) }
        var start = iterator.first()
        var end = iterator.next()
        while (end != BreakIterator.DONE) {
            addSentenceParts(block.text, start, end, chapterIndex, blockIndex, maxInputLength)
            start = end
            end = iterator.next()
        }
    }
}

private fun MutableList<TtsSentence>.addSentenceParts(
    source: String,
    rawStart: Int,
    rawEnd: Int,
    chapterIndex: Int,
    blockIndex: Int,
    maxInputLength: Int,
) {
    var start = rawStart
    var end = rawEnd
    while (start < end && source[start].isWhitespace()) start++
    while (end > start && source[end - 1].isWhitespace()) end--
    if (start >= end) return

    val safeLimit = maxInputLength.coerceAtLeast(2)
    while (end - start > safeLimit) {
        val hardEnd = safeCharBoundary(source, start + safeLimit)
        val split = findNaturalSplit(source, start, hardEnd).takeIf { it > start } ?: hardEnd
        var spokenEnd = split
        while (spokenEnd > start && source[spokenEnd - 1].isWhitespace()) spokenEnd--
        if (spokenEnd > start) {
            add(TtsSentence(TtsSentenceLocator(chapterIndex, blockIndex, start, spokenEnd), source.substring(start, spokenEnd)))
        }
        start = split
        while (start < end && source[start].isWhitespace()) start++
    }
    if (start < end) {
        add(TtsSentence(TtsSentenceLocator(chapterIndex, blockIndex, start, end), source.substring(start, end)))
    }
}

private fun safeCharBoundary(source: String, proposed: Int): Int =
    if (proposed in 1 until source.length && Character.isHighSurrogate(source[proposed - 1]) &&
        Character.isLowSurrogate(source[proposed])) proposed - 1 else proposed

private fun findNaturalSplit(source: String, start: Int, endExclusive: Int): Int {
    for (index in endExclusive - 1 downTo start + 1) {
        if (source[index].isWhitespace() || source[index] in NATURAL_BREAKS) return index + 1
    }
    return endExclusive
}

internal fun sentenceIndexForProgress(
    content: ReaderContent,
    sentences: List<TtsSentence>,
    progress: Float,
): Int {
    if (sentences.isEmpty()) return -1
    val position = content.positionAt(progress)
    val block = content.blocks.getOrNull(position.blockIndex)
    val offset = if (block is ReaderBlock.Text) (block.text.length * position.fractionInBlock).toInt() else 0
    return sentences.indexOfFirst { sentence ->
        sentence.locator.blockIndex > position.blockIndex ||
            (sentence.locator.blockIndex == position.blockIndex && sentence.locator.endOffset > offset)
    }.takeIf { it >= 0 } ?: sentences.size
}

internal fun ReaderContent.progressAt(sentence: TtsSentence): Float {
    val block = blocks.getOrNull(sentence.locator.blockIndex) as? ReaderBlock.Text ?: return 0f
    return progressAt(sentence.locator.blockIndex, sentence.locator.startOffset.toFloat() / block.text.length.coerceAtLeast(1))
}

private val NATURAL_BREAKS = setOf('。', '！', '？', '!', '?', '；', ';', '，', ',', '、', '…')
private const val DEFAULT_MAX_SPEECH_INPUT_LENGTH = 4_000
