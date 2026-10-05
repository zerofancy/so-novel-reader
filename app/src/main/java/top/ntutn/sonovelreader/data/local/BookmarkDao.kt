package top.ntutn.sonovelreader.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Transaction
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface BookmarkDao {
    @Query("SELECT * FROM bookmarks WHERE bookId = :bookId ORDER BY createdAt DESC, id DESC")
    fun observe(bookId: String): Flow<List<BookmarkEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM bookmarks WHERE bookId = :bookId AND chapterHref = :chapterHref AND ABS(chapterFraction - :fraction) <= 0.000001)")
    suspend fun containsPosition(bookId: String, chapterHref: String, fraction: Float): Boolean

    @Transaction
    suspend fun insertAtNewPosition(bookmark: BookmarkEntity): Boolean {
        if (containsPosition(bookmark.bookId, bookmark.chapterHref, bookmark.chapterFraction)) return false
        insert(bookmark)
        return true
    }

    @Insert
    suspend fun insert(bookmark: BookmarkEntity)

    @Query("UPDATE bookmarks SET name = :name, updatedAt = :updatedAt WHERE bookId = :bookId AND id = :id")
    suspend fun rename(bookId: String, id: String, name: String, updatedAt: Long)

    @Query("DELETE FROM bookmarks WHERE bookId = :bookId AND id = :id")
    suspend fun delete(bookId: String, id: String)
}
