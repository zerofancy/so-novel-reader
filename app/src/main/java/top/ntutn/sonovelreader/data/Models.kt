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
