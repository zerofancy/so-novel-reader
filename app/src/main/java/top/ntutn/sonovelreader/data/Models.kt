package top.ntutn.sonovelreader.data

import top.ntutn.sonovelreader.data.local.BookEntity
import top.ntutn.sonovelreader.data.local.BookWithProgress

enum class ReadingMode { SCROLL, PAGED }

enum class ReaderTheme { SYSTEM, LIGHT, DARK, SEPIA }

data class ReaderSettings(
    val readingMode: ReadingMode = ReadingMode.SCROLL,
    val fontSizeSp: Int = 20,
    val lineHeight: Float = 1.7f,
    val theme: ReaderTheme = ReaderTheme.SYSTEM,
    val keepScreenOn: Boolean = false,
    val ttsRate: Float = 1f,
    val ttsPitch: Float = 1f,
    val ttsVoiceName: String? = null,
)

data class ReaderLocator(
    val chapterHref: String,
    val chapterIndex: Int,
    val chapterFraction: Float,
)

data class TocItem(
    val title: String,
    val href: String,
    val fragment: String? = null,
    val depth: Int = 0,
)

data class ReaderChapter(
    val title: String,
    val href: String,
    val absolutePath: String,
)

sealed interface ReaderBlock {
    val weight: Int

    data class Text(val text: String) : ReaderBlock {
        override val weight: Int = text.length.coerceAtLeast(1)
    }

    data class Image(
        val absolutePath: String?,
        val contentDescription: String,
        val aspectRatio: Float?,
    ) : ReaderBlock {
        override val weight: Int = IMAGE_PROGRESS_WEIGHT
    }

    companion object {
        private const val IMAGE_PROGRESS_WEIGHT = 400
    }
}

internal const val EMPTY_CHAPTER_TEXT = "本章没有可显示的内容"

data class ReaderContentPosition(
    val blockIndex: Int,
    val fractionInBlock: Float = 0f,
)

data class ReaderContent(
    val blocks: List<ReaderBlock>,
    val anchors: Map<String, ReaderContentPosition> = emptyMap(),
) {
    val totalWeight: Int = blocks.sumOf(ReaderBlock::weight).coerceAtLeast(1)

    fun progressAt(blockIndex: Int, fractionInBlock: Float = 0f): Float {
        if (blocks.isEmpty()) return 0f
        val index = blockIndex.coerceIn(blocks.indices)
        val completed = blocks.take(index).sumOf(ReaderBlock::weight)
        val partial = blocks[index].weight * fractionInBlock.coerceIn(0f, 1f)
        return ((completed + partial) / totalWeight).coerceIn(0f, 1f)
    }

    fun positionAt(progress: Float): ReaderContentPosition {
        if (blocks.isEmpty()) return ReaderContentPosition(0)
        val target = progress.coerceIn(0f, 1f) * totalWeight
        var completed = 0
        blocks.forEachIndexed { index, block ->
            val end = completed + block.weight
            if (target <= end || index == blocks.lastIndex) {
                return ReaderContentPosition(
                    blockIndex = index,
                    fractionInBlock = ((target - completed) / block.weight).coerceIn(0f, 1f),
                )
            }
            completed = end
        }
        return ReaderContentPosition(blocks.lastIndex, 1f)
    }
}

sealed interface ReaderPageItem {
    val blockIndex: Int

    data class Text(
        override val blockIndex: Int,
        val text: String,
        val startOffset: Int,
        val endOffset: Int,
        val heightPx: Int,
    ) : ReaderPageItem

    data class Image(
        override val blockIndex: Int,
        val block: ReaderBlock.Image,
        val heightPx: Int,
    ) : ReaderPageItem
}

data class ReaderPage(
    val items: List<ReaderPageItem>,
    val startProgress: Float,
)

data class ParsedBook(
    val book: BookEntity,
    val chapters: List<ReaderChapter>,
    val toc: List<TocItem>,
)

data class ShelfBook(
    val id: String,
    val title: String,
    val authors: String,
    val coverPath: String?,
    val progress: Float?,
    val lastReadAt: Long?,
    val addedAt: Long,
)

fun BookWithProgress.toShelfBook(): ShelfBook = ShelfBook(
    id = book.id,
    title = book.title,
    authors = book.authors,
    coverPath = book.coverPath,
    progress = progress?.chapterFraction,
    lastReadAt = progress?.updatedAt,
    addedAt = book.addedAt,
)

sealed interface ImportItemResult {
    val displayName: String

    data class Imported(override val displayName: String, val bookId: String) : ImportItemResult
    data class Existing(override val displayName: String, val bookId: String) : ImportItemResult
    data class Failed(override val displayName: String, val message: String) : ImportItemResult
}

data class ImportBatchResult(val items: List<ImportItemResult>) {
    val importedCount: Int get() = items.count { it is ImportItemResult.Imported }
    val existingCount: Int get() = items.count { it is ImportItemResult.Existing }
    val failedCount: Int get() = items.count { it is ImportItemResult.Failed }
    val successfulBookIds: List<String> get() = items.mapNotNull {
        when (it) {
            is ImportItemResult.Imported -> it.bookId
            is ImportItemResult.Existing -> it.bookId
            is ImportItemResult.Failed -> null
        }
    }
}
