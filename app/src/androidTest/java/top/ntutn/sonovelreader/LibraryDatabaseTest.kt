package top.ntutn.sonovelreader

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import top.ntutn.sonovelreader.data.local.BookEntity
import top.ntutn.sonovelreader.data.local.LibraryDatabase
import top.ntutn.sonovelreader.data.local.ReadingProgressEntity

@RunWith(AndroidJUnit4::class)
class LibraryDatabaseTest {
    private lateinit var database: LibraryDatabase

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
