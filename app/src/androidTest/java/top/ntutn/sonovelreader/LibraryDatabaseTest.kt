package top.ntutn.sonovelreader

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import androidx.room.testing.MigrationTestHelper
import top.ntutn.sonovelreader.data.local.BookmarkEntity
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import top.ntutn.sonovelreader.data.local.BookEntity
import top.ntutn.sonovelreader.data.local.LibraryDatabase
import top.ntutn.sonovelreader.data.local.ReadingProgressEntity

@RunWith(AndroidJUnit4::class)
class LibraryDatabaseTest {
    @get:Rule val migrationHelper = MigrationTestHelper(
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation(), LibraryDatabase::class.java)

    private lateinit var database: LibraryDatabase

    @Test fun migratesHistoricalVersionsWithoutLosingBooks() {
        for (version in listOf(1, 2)) {
            val name = "bookmark-migration-$version"
            migrationHelper.createDatabase(name, version).apply {
                execSQL("INSERT INTO books (id,title,authors,sourceFileName,epubPath,contentDirectory,coverPath,contentHash,addedAt) VALUES ('old','old','author','old.epub','/old.epub','/old',NULL,'hash',1)")
                execSQL("INSERT INTO reading_progress (bookId,chapterHref,chapterIndex,chapterFraction,updatedAt) VALUES ('old','chapter.xhtml',0,0.5,2)")
                close()
            }
            migrationHelper.runMigrationsAndValidate(name, 3, true).apply {
                query("SELECT chapterFraction FROM reading_progress WHERE bookId = 'old'").use {
                    assertEquals(true, it.moveToFirst())
                    assertEquals(0.5f, it.getFloat(0))
                }
                execSQL("INSERT INTO bookmarks VALUES ('b','old','name','chapter.xhtml',0,'chapter',0.5,1,1)")
                close()
            }
        }
    }

    @Test fun duplicatePositionsAreRejectedAtomically() = runBlocking {
        database.libraryDao().insertBook(book("a", 1))
        database.libraryDao().insertBook(book("b", 2))
        val repository = top.ntutn.sonovelreader.data.BookmarkRepository(database.bookmarkDao())
        val locator = top.ntutn.sonovelreader.data.ReaderLocator("chapter.xhtml", 0, 0.5f)
        val results = kotlinx.coroutines.coroutineScope {
            (1..8).map { index -> async {
                try { repository.add("a", "name-$index", locator, "chapter"); true
                } catch (_: top.ntutn.sonovelreader.data.DuplicateBookmarkException) { false }
            } }.map { it.await() }
        }
        assertEquals(1, results.count { it })
        try {
            repository.add("a", "different name", locator.copy(chapterFraction = 0.5000001f), "chapter")
            org.junit.Assert.fail("Float noise must not create another bookmark")
        } catch (_: top.ntutn.sonovelreader.data.DuplicateBookmarkException) { }
        repository.add("a", "same name", locator.copy(chapterFraction = 0.6f), "chapter")
        repository.add("a", "same name", locator.copy(chapterHref = "other.xhtml"), "other")
        repository.add("b", "same name", locator, "chapter")
        assertEquals(3, database.bookmarkDao().observe("a").first().size)
        assertEquals(1, database.bookmarkDao().observe("b").first().size)
    }

    @Test fun bookmarksAreScopedSortedEditableAndCascade() = runBlocking {
        val books = database.libraryDao()
        books.insertBook(book("a", 1)); books.insertBook(book("b", 2))
        val dao = database.bookmarkDao()
        fun mark(id: String, bookId: String, time: Long) = BookmarkEntity(id, bookId, "same", "chapter.xhtml", 0, "chapter", 0.5f, time, time)
        dao.insert(mark("old", "a", 1)); dao.insert(mark("new", "a", 2)); dao.insert(mark("other", "b", 3))
        assertEquals(listOf("new", "old"), dao.observe("a").first().map { it.id })
        dao.rename("b", "new", "wrong", 4)
        assertEquals("same", dao.observe("a").first().first().name)
        dao.rename("a", "old", "renamed", 5)
        assertEquals("renamed", dao.observe("a").first().last().name)
        dao.delete("b", "new")
        assertEquals(2, dao.observe("a").first().size)
        books.deleteBook(book("a", 1))
        assertEquals(emptyList<BookmarkEntity>(), dao.observe("a").first())
        assertEquals(1, dao.observe("b").first().size)
    }


    @Before
    fun createDatabase() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, LibraryDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun closeDatabase() = database.close()

    @Test
    fun shelfIsSortedByLastReadAndProgressCascadesOnDelete() = runBlocking {
        val dao = database.libraryDao()
        val older = book("older", 100)
        val newer = book("newer", 200)
        dao.insertBook(older)
        dao.insertBook(newer)
        dao.saveProgress(ReadingProgressEntity("older", "chapter.xhtml", 0, 0.5f, 300))

        assertEquals(listOf("older", "newer"), dao.observeShelf().first().map { it.book.id })
        dao.deleteBook(older)
        assertNull(dao.getProgress("older"))
    }

    private fun book(id: String, addedAt: Long) = BookEntity(
        id = id,
        title = id,
        authors = "作者",
        sourceFileName = "$id.epub",
        epubPath = "/tmp/$id.epub",
        contentDirectory = "/tmp/$id",
        coverPath = null,
        contentHash = "hash-$id",
        addedAt = addedAt,
    )
}
