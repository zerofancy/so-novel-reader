package top.ntutn.sonovelreader.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface LibraryDao {
    // ===========================
    // 分组（category） CRUD
    // ===========================

    @Query("SELECT * FROM categories ORDER BY sortOrder, createdAt")
    fun observeGroups(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories ORDER BY sortOrder, createdAt")
    suspend fun getGroups(): List<CategoryEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertGroup(group: CategoryEntity)

    @Update
    suspend fun updateGroup(group: CategoryEntity)

    @Delete
    suspend fun deleteGroup(group: CategoryEntity)

    @Query("UPDATE categories SET sortOrder = :sortOrder WHERE id = :id")
    suspend fun updateGroupOrder(id: String, sortOrder: Int)

    /**
     * 分组-册数 聚合行。
     */
    data class GroupBookCountRow(val categoryId: String, val bookCount: Int)

    /**
     * 不包含未分组（categoryId IS NULL）条目
     */
    @Query(
        """
        SELECT categoryId AS categoryId, COUNT(*) AS bookCount
        FROM books
        WHERE categoryId IS NOT NULL
        GROUP BY categoryId
        """,
    )
    fun observeGroupBookCounts(): Flow<List<GroupBookCountRow>>

    // ===========================
    // 书架查询（按分组）
    // ===========================

    @Transaction
    @Query(
        """
        SELECT * FROM books
        WHERE categoryId IS NULL
        ORDER BY COALESCE(
            (SELECT updatedAt FROM reading_progress WHERE reading_progress.bookId = books.id),
            addedAt
        ) DESC
        """,
    )
    fun observeTopLevelBooks(): Flow<List<BookWithProgress>>

    @Transaction
    @Query(
        """
        SELECT * FROM books
        WHERE categoryId = :groupId
        ORDER BY COALESCE(
            (SELECT updatedAt FROM reading_progress WHERE reading_progress.bookId = books.id),
            addedAt
        ) DESC
        """,
    )
    fun observeBooksInGroup(groupId: String): Flow<List<BookWithProgress>>

    @Transaction
    @Query(
        """
        SELECT * FROM books
        ORDER BY COALESCE(
            (SELECT updatedAt FROM reading_progress WHERE reading_progress.bookId = books.id),
            addedAt
        ) DESC
        """,
    )
    fun observeShelf(): Flow<List<BookWithProgress>>

    @Query("SELECT * FROM books WHERE id = :id")
    suspend fun getBook(id: String): BookEntity?

    @Query("SELECT * FROM books WHERE contentHash = :hash LIMIT 1")
    suspend fun getBookByHash(hash: String): BookEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertBook(book: BookEntity)

    @Delete
    suspend fun deleteBook(book: BookEntity)

    // ===========================
    // 移动书籍
    // ===========================

    @Query("UPDATE books SET categoryId = :groupId WHERE id = :bookId")
    suspend fun updateBookGroup(bookId: String, groupId: String?)

    // ===========================
    // 阅读进度
    // ===========================

    @Query("SELECT * FROM reading_progress WHERE bookId = :bookId")
    fun observeProgress(bookId: String): Flow<ReadingProgressEntity?>

    @Query("SELECT * FROM reading_progress WHERE bookId = :bookId")
    suspend fun getProgress(bookId: String): ReadingProgressEntity?

    @Upsert
    suspend fun saveProgress(progress: ReadingProgressEntity)
}
