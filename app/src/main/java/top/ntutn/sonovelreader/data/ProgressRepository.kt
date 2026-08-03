package top.ntutn.sonovelreader.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import top.ntutn.sonovelreader.data.local.LibraryDao
import top.ntutn.sonovelreader.data.local.ReadingProgressEntity

class ProgressRepository(private val dao: LibraryDao) {
    fun observe(bookId: String): Flow<ReaderLocator?> = dao.observeProgress(bookId).map { it?.toLocator() }

    suspend fun get(bookId: String): ReaderLocator? = dao.getProgress(bookId)?.toLocator()

    suspend fun save(bookId: String, locator: ReaderLocator) {
        dao.saveProgress(
            ReadingProgressEntity(
                bookId = bookId,
                chapterHref = locator.chapterHref,
                chapterIndex = locator.chapterIndex.coerceAtLeast(0),
                chapterFraction = locator.chapterFraction.coerceIn(0f, 1f),
                updatedAt = System.currentTimeMillis(),
            ),
        )
    }
}

private fun ReadingProgressEntity.toLocator() = ReaderLocator(
    chapterHref = chapterHref,
    chapterIndex = chapterIndex,
    chapterFraction = chapterFraction.coerceIn(0f, 1f),
)
