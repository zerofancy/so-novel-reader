package top.ntutn.sonovelreader

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collect
import org.junit.*
import org.junit.Assert.*
import top.ntutn.sonovelreader.data.BookRepository
import top.ntutn.sonovelreader.data.local.*

class ShelfOrganizationTest {
    private lateinit var db: LibraryDatabase
    private val dao get() = db.libraryDao()
    @Before fun create() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), LibraryDatabase::class.java).build()
        runBlocking { listOf("a", "b", "c", "d").forEach { dao.insertBook(book(it)) } }
    }
    @After fun close() = db.close()
    private fun book(id: String) = BookEntity(id, id, "author", "$id.epub", "/$id.epub", "/$id", null, id, 1)

    @Test fun groupingIsPersistentNamedAndPreservesRelatedData() = runBlocking {
        val progress = ReadingProgressEntity("a", "chapter", 0, .5f, 10)
        val bookmark = BookmarkEntity("mark", "a", "name", "chapter", 0, "Chapter", .5f, 1, 1)
        dao.saveProgress(progress); db.bookmarkDao().insert(bookmark)
        dao.insertGroup(CategoryEntity("existing", "未命名分类", 3, 1))
        assertEquals("未命名分类 2", dao.groupTwoBooks("a", "b"))
        val group = dao.getGroups().last()
        assertEquals(4, group.sortOrder)
        assertEquals(group.id, dao.getBook("a")!!.categoryId)
        assertEquals(group.id, dao.getBook("b")!!.categoryId)
        assertEquals(book("a").copy(categoryId = group.id), dao.getBook("a"))
        assertEquals(progress, dao.getProgress("a"))
        assertEquals(bookmark, db.bookmarkDao().observe("a").first().single())
        assertEquals(group.name, dao.dropBookIntoGroup("c", group.id))
        val repo = BookRepository(ApplicationProvider.getApplicationContext(), dao)
        val snapshot = repo.observeTopLevelShelf().first()
        assertEquals(3, snapshot.first.last().bookCount)
        assertEquals(listOf("d"), snapshot.second.map { it.id })
    }

    @Test fun invalidAndRepeatedOperationsNeverLeaveNewGroups() = runBlocking {
        suspend fun invalid(block: suspend () -> Unit) {
            try { block(); fail("Expected stale shelf rejection") } catch (_: ShelfChangedException) { }
        }
        invalid { dao.groupTwoBooks("a", "a") }
        invalid { dao.groupTwoBooks("a", "missing") }
        invalid { dao.dropBookIntoGroup("a", "missing") }
        assertTrue(dao.getGroups().isEmpty())
        dao.groupTwoBooks("a", "b")
        invalid { dao.groupTwoBooks("a", "c") }
        invalid { dao.dropBookIntoGroup("a", dao.getGroups().single().id) }
        assertEquals(1, dao.getGroups().size)
        assertNull(dao.getBook("c")!!.categoryId)
    }

    @Test fun failureOnSecondBookRollsBackBothMovesAndGroup() = runBlocking {
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_second BEFORE UPDATE OF categoryId ON books WHEN OLD.id = 'b' BEGIN SELECT RAISE(ABORT, 'injected failure'); END")
        try { dao.groupTwoBooks("a", "b"); fail("Expected injected failure") } catch (_: android.database.sqlite.SQLiteException) { }
        assertTrue(dao.getGroups().isEmpty())
        assertNull(dao.getBook("a")!!.categoryId)
        assertNull(dao.getBook("b")!!.categoryId)
    }

    @Test fun simultaneousOppositeDropsOnlyCreateOneGroup() = runBlocking {
        val results = coroutineScope {
            listOf(async(Dispatchers.IO) { runCatching { dao.groupTwoBooks("a", "b") }.isSuccess },
                async(Dispatchers.IO) { runCatching { dao.groupTwoBooks("b", "a") }.isSuccess }).awaitAll()
        }
        assertEquals(1, results.count { it })
        assertEquals(1, dao.getGroups().size)
        assertEquals(dao.getBook("a")!!.categoryId, dao.getBook("b")!!.categoryId)
    }

    @Test fun concurrentIndependentPairsGetDifferentNames() = runBlocking {
        val names = coroutineScope {
            listOf(async(Dispatchers.IO) { dao.groupTwoBooks("a", "b") },
                async(Dispatchers.IO) { dao.groupTwoBooks("c", "d") }).awaitAll()
        }
        assertEquals(setOf("未命名分类", "未命名分类 2"), names.toSet())
    }
    @Test fun shelfFlowPublishesCoherentSnapshotsEvenWhenTableCountsDoNotChange() = runBlocking {
        val repo = BookRepository(ApplicationProvider.getApplicationContext(), dao)
        val updates = Channel<Pair<List<top.ntutn.sonovelreader.data.ShelfGroup>, List<top.ntutn.sonovelreader.data.ShelfBook>>>(Channel.UNLIMITED)
        val collector = launch { repo.observeTopLevelShelf().collect { updates.send(it) } }
        suspend fun next() = withTimeout(5000) { updates.receive() }
        try {
            assertEquals(4, next().second.size)
            dao.groupTwoBooks("a", "b")
            val grouped = next()
            assertEquals(2, grouped.first.single().bookCount)
            assertEquals(setOf("c", "d"), grouped.second.map { it.id }.toSet())
            dao.dropBookIntoGroup("c", grouped.first.single().id)
            val moved = next()
            assertEquals(3, moved.first.single().bookCount)
            assertEquals(listOf("d"), moved.second.map { it.id })
        } finally { collector.cancelAndJoin(); updates.close() }
    }

}
