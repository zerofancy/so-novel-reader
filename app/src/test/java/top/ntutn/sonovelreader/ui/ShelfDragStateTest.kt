package top.ntutn.sonovelreader.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import org.junit.Assert.*
import org.junit.Test
import top.ntutn.sonovelreader.data.ShelfBook

class ShelfDragStateTest {
    private val book = ShelfBook("a", "A", "author", null, null, null, 0)

    @Test fun hitTestingExcludesSourceMenusAndOverlaysAndRequiresMovement() {
        val drag = ShelfDragState()
        val source = ShelfDropTarget.Book("a")
        val target = ShelfDropTarget.Book("b")
        drag.viewport = Rect(0f, 0f, 300f, 500f)
        drag.items[source] = ShelfDragItem(source, "A", book)
        drag.items[target] = ShelfDragItem(target, "B", book.copy(id = "b"))
        drag.covers[source] = Rect(0f, 0f, 100f, 160f)
        drag.covers[target] = Rect(150f, 0f, 250f, 160f)
        drag.menus[target] = Rect(210f, 0f, 250f, 40f)
        drag.session = ShelfDragSession(book, Offset.Zero, Offset.Zero, Offset.Zero, 100f)
        assertNull(drag.targetAt(Offset(180f, 80f)))
        drag.session = drag.session!!.copy(dragging = true)
        assertEquals(target, drag.targetAt(Offset(180f, 80f))?.target)
        assertNull(drag.targetAt(Offset(50f, 80f)))
        assertNull(drag.targetAt(Offset(230f, 20f)))
        assertNull(drag.targetAt(Offset(180f, 170f)))
        drag.importButton = Rect(160f, 60f, 220f, 120f)
        assertNull(drag.targetAt(Offset(180f, 80f)))
        drag.cancel()
        assertNull(drag.targetAt(Offset(180f, 80f)))
    }

    @Test fun edgeScrollingScalesWithDistanceAndStopsOutsideViewport() {
        assertEquals(0f, shelfEdgeVelocity(200f, 0f, 500f, 48f, 600f))
        assertEquals(-600f, shelfEdgeVelocity(0f, 0f, 500f, 48f, 600f))
        assertEquals(-300f, shelfEdgeVelocity(24f, 0f, 500f, 48f, 600f))
        assertEquals(600f, shelfEdgeVelocity(500f, 0f, 500f, 48f, 600f))
        assertEquals(0f, shelfEdgeVelocity(501f, 0f, 500f, 48f, 600f))
    }
}
