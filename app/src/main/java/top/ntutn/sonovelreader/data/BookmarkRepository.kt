package top.ntutn.sonovelreader.data

import java.util.UUID
import top.ntutn.sonovelreader.data.local.BookmarkDao
import top.ntutn.sonovelreader.data.local.BookmarkEntity

fun validBookmarkName(name: String): Boolean = name.trim().let {
    it.isNotBlank() && it.codePointCount(0, it.length) in 1..50
}

class DuplicateBookmarkException : IllegalStateException("该位置已有书签")

class BookmarkRepository(private val dao: BookmarkDao) {
    fun observe(bookId: String) = dao.observe(bookId)

    suspend fun add(bookId: String, name: String, locator: ReaderLocator, title: String) {
        require(validBookmarkName(name))
        require(locator.chapterFraction.isFinite())
        val now = System.currentTimeMillis()
        val inserted = dao.insertAtNewPosition(BookmarkEntity(UUID.randomUUID().toString(), bookId, name.trim(),
            locator.chapterHref, locator.chapterIndex, title,
            locator.chapterFraction.coerceIn(0f, 1f), now, now))
        if (!inserted) throw DuplicateBookmarkException()
    }

    suspend fun rename(bookId: String, id: String, name: String) {
        require(validBookmarkName(name))
        dao.rename(bookId, id, name.trim(), System.currentTimeMillis())
    }

    suspend fun delete(bookId: String, id: String) = dao.delete(bookId, id)
}
