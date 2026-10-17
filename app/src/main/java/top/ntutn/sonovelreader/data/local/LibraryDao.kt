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
import top.ntutn.sonovelreader.data.nextUnnamedGroupName
import java.util.UUID

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
    suspend fun updateBookGroup(bookId: String, groupId: String?): Int


    /** All shelf tables invalidate this signal; the actual snapshot is read in one transaction. */
    @Query("SELECT (SELECT COUNT(*) FROM books) + (SELECT COUNT(*) FROM categories) + (SELECT COUNT(*) FROM reading_progress)")
    fun observeShelfChanges(): Flow<Int>

    @Transaction
    @Query("SELECT * FROM books ORDER BY COALESCE((SELECT updatedAt FROM reading_progress WHERE reading_progress.bookId = books.id), addedAt) DESC")
    suspend fun getShelfBooks(): List<BookWithProgress>

    data class ShelfSnapshot(val groups: List<CategoryEntity>, val books: List<BookWithProgress>)

    @Transaction
    suspend fun getShelfSnapshot(): ShelfSnapshot = ShelfSnapshot(getGroups(), getShelfBooks())

    @Query("UPDATE books SET categoryId = :groupId WHERE id IN (:bookIds) AND categoryId IS NULL")
    suspend fun moveUngroupedBooks(bookIds: List<String>, groupId: String): Int

    @Transaction
    suspend fun dropBookIntoGroup(bookId: String, groupId: String): String {
        val book = getBook(bookId)
        val group = getGroups().find { it.id == groupId }
        if (book == null || book.categoryId != null || group == null) throw ShelfChangedException()
        if (moveUngroupedBooks(listOf(bookId), groupId) != 1) throw ShelfChangedException()
        return group.name
    }

    @Transaction
    suspend fun groupTwoBooks(sourceId: String, targetId: String): String {
        if (sourceId == targetId) throw ShelfChangedException()
        val source = getBook(sourceId)
        val target = getBook(targetId)
        if (source == null || target == null || source.categoryId != null || target.categoryId != null) {
            throw ShelfChangedException()
        }
        val groups = getGroups()
        val name = nextUnnamedGroupName(groups.map { it.name }.toSet())
        val groupId = UUID.randomUUID().toString()
        insertGroup(CategoryEntity(groupId, name, (groups.maxOfOrNull { it.sortOrder } ?: -1) + 1, System.currentTimeMillis()))
        if (moveUngroupedBooks(listOf(sourceId, targetId), groupId) != 2) throw ShelfChangedException()
        return name
    }

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

class ShelfChangedException : IllegalStateException("书籍或分类已变化，请重新拖动")
