package top.ntutn.sonovelreader.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.Composable
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import top.ntutn.sonovelreader.data.ShelfBook
import top.ntutn.sonovelreader.data.ShelfGroup

class BookshelfDragUiTest {
    @get:Rule val rule = createComposeRule()
    private val books = listOf("a", "b").map { ShelfBook(it, "书籍 $it", "作者", null, null, null, 1) }
    private val initial = LibraryUiState(groups = listOf(ShelfGroup("g", "历史", 0)), ungroupedBooks = books)
    private var opened: String? = null
    private var dropped: Pair<String, ShelfDropTarget>? = null
    private var moved: String? = null

    @Composable private fun Shelf(state: LibraryUiState, onDrop: (String, ShelfDropTarget) -> Unit = { id, target -> dropped = id to target }) {
        MaterialTheme {
            BookshelfScreen(state, {}, { opened = it }, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {},
                { id, _, _ -> moved = id }, {}, {}, {}, onDrop)
        }
    }
    private fun drag(sourceTag: String, targetTag: String) {
        val root = rule.onNodeWithTag("shelf-grid").fetchSemanticsNode().boundsInRoot.topLeft
        val source = rule.onNodeWithTag(sourceTag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.center - root
        val target = rule.onNodeWithTag(targetTag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.center - root
        rule.onNodeWithTag("shelf-grid").performTouchInput {
            down(source)
            advanceEventTime(700)
            moveTo(target, delayMillis = 300)
            up()
        }
        rule.waitForIdle()
    }

    @Test fun dropsBookIntoGroupAndDoesNotOpenReader() {
        rule.setContent { Shelf(initial) }
        drag("shelf-cover-a", "shelf-group-g")
        assertEquals("a" to ShelfDropTarget.Group("g"), dropped)
        assertNull(opened)
        rule.onNodeWithTag("shelf-drag-preview").assertDoesNotExist()
    }

    @Test fun dropsBookOnBookAndRejectsSelfDrop() {
        rule.setContent { Shelf(initial) }
        drag("shelf-cover-a", "shelf-cover-b")
        assertEquals("a" to ShelfDropTarget.Book("b"), dropped)
        dropped = null
        drag("shelf-cover-a", "shelf-cover-a")
        assertNull(dropped)
        assertNull(opened)
    }

    @Test fun tapAndMoreMenuRemainAvailableAndLongPressAloneDoesNothing() {
        rule.setContent { Shelf(initial) }
        rule.onNodeWithTag("shelf-book-a").performTouchInput { longClick() }
        assertNull(opened)
        assertNull(dropped)
        rule.onNodeWithText("移动到分组…").assertDoesNotExist()
        rule.onNodeWithTag("shelf-book-a").performClick()
        assertEquals("a", opened)
        rule.onNodeWithContentDescription("书籍『书籍 a』的更多操作").performClick()
        rule.onNodeWithText("移动到分组…").performClick()
        assertEquals("a", moved)
    }

    @Test fun accessibilityCanGroupWithoutDragging() {
        rule.setContent { Shelf(initial) }
        val actions = rule.onNodeWithTag("shelf-book-a").fetchSemanticsNode().config[SemanticsActions.CustomActions]
        rule.runOnIdle { assertTrue(actions.first { it.label == "与其他书籍创建分类" }.action()) }
        rule.onAllNodesWithText("书籍 b").onLast().performClick()
        assertEquals("a" to ShelfDropTarget.Book("b"), dropped)
    }

    @Test fun busyStateBlocksDragAndCardActions() {
        rule.setContent { Shelf(initial.copy(organizing = true)) }
        drag("shelf-cover-a", "shelf-group-g")
        assertNull(dropped)
        rule.onNodeWithTag("shelf-book-a").performTouchInput { click() }
        assertNull(opened)
    }

    @Test fun secondPointerCancelsDrop() {
        rule.setContent { Shelf(initial) }
        val root = rule.onNodeWithTag("shelf-grid").fetchSemanticsNode().boundsInRoot.topLeft
        val source = rule.onNodeWithTag("shelf-cover-a", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.center - root
        val target = rule.onNodeWithTag("shelf-group-g", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.center - root
        rule.onNodeWithTag("shelf-grid").performTouchInput {
            down(0, source); advanceEventTime(700)
            moveTo(0, target, delayMillis = 200)
            down(1, source); up(1); up(0)
        }
        assertNull(dropped)
        assertNull(opened)
        rule.onNodeWithTag("shelf-drag-preview").assertDoesNotExist()
    }
    @Test fun dragSurvivesSourceRecyclingAndScrollsBackToTopGroup() {
        val many = (0..39).map { books.first().copy(id = "book-$it", title = "Book $it") }
        rule.setContent { Shelf(initial.copy(ungroupedBooks = many)) }
        rule.onNodeWithTag("shelf-grid").performScrollToIndex(25)
        val root = rule.onNodeWithTag("shelf-grid").fetchSemanticsNode().boundsInRoot.topLeft
        val source = rule.onNodeWithTag("shelf-cover-book-25", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.center - root
        rule.mainClock.autoAdvance = false
        try {
            rule.onNodeWithTag("shelf-grid").performTouchInput { down(source) }
            rule.mainClock.advanceTimeBy(700)
            rule.onNodeWithTag("shelf-grid").performTouchInput { moveTo(Offset(25f, 2f)) }
            rule.mainClock.advanceTimeBy(6000)
            // The initial source has scrolled offscreen; the shared gesture must still be alive.
            rule.onNodeWithTag("shelf-drag-preview").assertExists()
            val target = rule.onNodeWithTag("shelf-group-g", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.center - root
            rule.onNodeWithTag("shelf-grid").performTouchInput { moveTo(target) }
            rule.mainClock.advanceTimeBy(32)
            val app = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.app.Application>()
            java.io.File(app.externalCacheDir, "bookshelf-drag.png").outputStream().use {
                rule.onRoot().captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
            rule.onNodeWithTag("shelf-grid").performTouchInput { up() }
        } finally { rule.mainClock.autoAdvance = true }
        rule.waitForIdle()
        assertEquals("book-25" to ShelfDropTarget.Group("g"), dropped)
        assertNull(opened)
    }

    @Test fun changingShelfDataCancelsAnActiveDrag() {
        val state = mutableStateOf(initial)
        rule.setContent { Shelf(state.value) }
        val root = rule.onNodeWithTag("shelf-grid").fetchSemanticsNode().boundsInRoot.topLeft
        val source = rule.onNodeWithTag("shelf-cover-a", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.center - root
        val target = rule.onNodeWithTag("shelf-group-g", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.center - root
        rule.mainClock.autoAdvance = false
        try {
            rule.onNodeWithTag("shelf-grid").performTouchInput { down(source) }
            rule.mainClock.advanceTimeBy(700)
            rule.onNodeWithTag("shelf-grid").performTouchInput { moveTo(target) }
            rule.runOnUiThread { state.value = initial.copy(groups = emptyList()) }
            rule.mainClock.advanceTimeBy(100)
            rule.onNodeWithTag("shelf-grid").performTouchInput { up() }
        } finally { rule.mainClock.autoAdvance = true }
        rule.waitForIdle()
        assertNull(dropped)
        assertNull(opened)
        rule.onNodeWithTag("shelf-drag-preview").assertDoesNotExist()
    }

    @Test fun backCancelsWithoutDroppingOrOpeningBook() {
        rule.setContent { Shelf(initial) }
        val root = rule.onNodeWithTag("shelf-grid").fetchSemanticsNode().boundsInRoot.topLeft
        val source = rule.onNodeWithTag("shelf-cover-a", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.center - root
        rule.mainClock.autoAdvance = false
        try {
            rule.onNodeWithTag("shelf-grid").performTouchInput { down(source) }
            rule.mainClock.advanceTimeBy(700)
            rule.onNodeWithTag("shelf-drag-preview").assertExists()
            androidx.test.espresso.Espresso.pressBack()
            rule.onNodeWithTag("shelf-grid").performTouchInput { up() }
        } finally { rule.mainClock.autoAdvance = true }
        rule.waitForIdle()
        assertNull(dropped)
        assertNull(opened)
        rule.onNodeWithTag("shelf-drag-preview").assertDoesNotExist()
    }

    @Test fun ordinarySwipeAndSecondPointerBeforeLongPressDoNotDrop() {
        rule.setContent { Shelf(initial) }
        rule.onNodeWithTag("shelf-grid").performTouchInput { swipeUp() }
        assertNull(dropped)
        assertNull(opened)
        rule.onNodeWithTag("shelf-grid").performScrollToIndex(0)
        val root = rule.onNodeWithTag("shelf-grid").fetchSemanticsNode().boundsInRoot.topLeft
        val source = rule.onNodeWithTag("shelf-cover-a", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.center - root
        val target = rule.onNodeWithTag("shelf-group-g", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.center - root
        rule.onNodeWithTag("shelf-grid").performTouchInput {
            down(0, source); advanceEventTime(100); down(1, target)
            advanceEventTime(700); up(1); moveTo(0, target); up(0)
        }
        assertNull(dropped)
        assertNull(opened)
    }

    @Test fun importedBooksAreGroupedThroughViewModelAndDatabase() {
        val app = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.app.Application>()
        val container = top.ntutn.sonovelreader.AppContainer(app)
        val stamp = System.nanoTime()
        val files = (1..2).map { index ->
            java.io.File(app.cacheDir, "drag-$stamp-$index.epub").also { file ->
                val book = io.documentnode.epub4j.domain.Book().apply {
                    metadata.addTitle("拖拽测试 $index")
                    addSection("章节", io.documentnode.epub4j.domain.Resource(
                        "<html><body><p>拖拽测试 $stamp $index</p></body></html>".toByteArray(), "chapter.xhtml"))
                }
                file.outputStream().use { io.documentnode.epub4j.epub.EpubWriter().write(book, it) }
            }
        }
        val ids = runBlocking { container.bookRepository.importBooks(files.map { android.net.Uri.fromFile(it) }).successfulBookIds }
        val oldGroups = runBlocking { container.bookRepository.getGroups().map { it.id }.toSet() }
        val store = androidx.lifecycle.ViewModelStore()
        lateinit var vm: LibraryViewModel
        var createdGroup: String? = null
        try {
            assertEquals(2, ids.size)
            rule.runOnIdle { vm = LibraryViewModel(container); store.put("library", vm) }
            rule.waitUntil(5000) { vm.state.value.ungroupedBooks.count { it.id in ids } == 2 }
            rule.setContent { val state by vm.state.collectAsState(); Shelf(state, vm::dropBook) }
            drag("shelf-cover-${ids[0]}", "shelf-cover-${ids[1]}")
            rule.waitUntil(5000) { !vm.state.value.organizing && vm.state.value.ungroupedBooks.none { it.id in ids } }
            val group = vm.state.value.groups.single { it.id !in oldGroups }
            createdGroup = group.id
            assertEquals(2, group.bookCount)
            assertEquals(ids.toSet(), runBlocking { container.bookRepository.observeGroupShelf(group.id).first().map { it.id }.toSet() })
            assertNull(opened)
        } finally {
            rule.runOnIdle { store.clear() }
            runBlocking {
                val newGroupIds = createdGroup?.let { listOf(it) } ?: container.bookRepository.getGroups()
                    .filter { it.id !in oldGroups }
                    .filter { group ->
                        val members = container.bookRepository.observeGroupShelf(group.id).first()
                        members.isNotEmpty() && members.all { it.id in ids }
                    }.map { it.id }
                ids.forEach { container.bookRepository.deleteBook(it) }
                newGroupIds.forEach { container.bookRepository.deleteGroup(it) }
            }
            files.forEach { it.delete() }
        }
    }

    @Test fun invalidDropAnimatesBackAndBlocksClicksUntilSettled() {
        rule.setContent { Shelf(initial) }
        val gridBounds = rule.onNodeWithTag("shelf-grid").fetchSemanticsNode().boundsInRoot
        val sourceBounds = rule.onNodeWithTag("shelf-book-a").fetchSemanticsNode().boundsInRoot
        val source = rule.onNodeWithTag("shelf-cover-a", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.center - gridBounds.topLeft
        rule.mainClock.autoAdvance = false
        try {
            rule.onNodeWithTag("shelf-grid").performTouchInput { down(source) }
            rule.mainClock.advanceTimeBy(700)
            rule.onNodeWithTag("shelf-grid").performTouchInput { moveTo(Offset(1f, gridBounds.height / 2)); up() }
            rule.mainClock.advanceTimeBy(32)
            rule.onNodeWithTag("shelf-drag-preview").assertDoesNotExist()
            rule.onNodeWithTag("shelf-drag-hint").assertDoesNotExist()
            val before = rule.onNodeWithTag("shelf-return-preview").fetchSemanticsNode().boundsInRoot.topLeft
            rule.mainClock.advanceTimeBy(100)
            val during = rule.onNodeWithTag("shelf-return-preview").fetchSemanticsNode().boundsInRoot.topLeft
            assertTrue("Preview should move toward its original card", (during - sourceBounds.topLeft).getDistance() < (before - sourceBounds.topLeft).getDistance())
            rule.onNodeWithTag("shelf-grid").performTouchInput { click(source) }
            rule.mainClock.advanceTimeBy(400)
            rule.onNodeWithTag("shelf-return-preview").assertDoesNotExist()
            rule.onNodeWithTag("shelf-book-a").assertIsDisplayed()
        } finally { rule.mainClock.autoAdvance = true }
        assertNull(opened)
        assertNull(dropped)
        // Once settled, the normal book tap must work again.
        rule.onNodeWithTag("shelf-book-a").performClick()
        assertEquals("a", opened)
    }

    @Test fun invalidDropRevealsRecycledSourceBeforeReturning() {
        val many = (0..39).map { books.first().copy(id = "book-$it", title = "Book $it") }
        rule.setContent { Shelf(initial.copy(ungroupedBooks = many)) }
        rule.onNodeWithTag("shelf-grid").performScrollToIndex(25)
        val gridBounds = rule.onNodeWithTag("shelf-grid").fetchSemanticsNode().boundsInRoot
        val source = rule.onNodeWithTag("shelf-cover-book-25", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.center - gridBounds.topLeft
        rule.mainClock.autoAdvance = false
        try {
            rule.onNodeWithTag("shelf-grid").performTouchInput { down(source) }
            rule.mainClock.advanceTimeBy(700)
            rule.onNodeWithTag("shelf-grid").performTouchInput { moveTo(Offset(25f, 2f)) }
            rule.mainClock.advanceTimeBy(6000)
            rule.onNodeWithTag("shelf-group-g", useUnmergedTree = true).assertIsDisplayed()
            rule.onNodeWithTag("shelf-grid").performTouchInput { moveTo(Offset(1f, gridBounds.height / 2)); up() }
            rule.mainClock.advanceTimeBy(32)
            rule.onNodeWithTag("shelf-return-preview").assertExists()
            rule.mainClock.advanceTimeBy(5000)
            rule.onNodeWithTag("shelf-return-preview").assertDoesNotExist()
            rule.onNodeWithTag("shelf-book-book-25").assertIsDisplayed()
        } finally { rule.mainClock.autoAdvance = true }
        assertNull(opened)
        assertNull(dropped)
    }

}
