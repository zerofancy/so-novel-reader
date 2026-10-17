package top.ntutn.sonovelreader.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil3.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import java.io.File
import kotlin.math.roundToInt
import kotlinx.coroutines.isActive
import top.ntutn.sonovelreader.data.ShelfBook

sealed interface ShelfDropTarget {
    val id: String
    data class Group(override val id: String) : ShelfDropTarget
    data class Book(override val id: String) : ShelfDropTarget
}

internal data class ShelfDragItem(val target: ShelfDropTarget, val name: String, val book: ShelfBook? = null)
internal data class ShelfDragSession(
    val book: ShelfBook,
    val position: Offset,
    val initialPosition: Offset,
    val grabOffset: Offset,
    val width: Float,
    val dragging: Boolean = false,
    val returning: Boolean = false,
    val originalTopLeft: Offset = initialPosition - grabOffset,
    val scrollIndex: Int = 0,
    val scrollOffset: Int = 0,
)

/** Coordinates live in root space, including visible/clipped target bounds. */
@Stable
internal class ShelfDragState(val grid: LazyGridState? = null) {
    val items = mutableStateMapOf<ShelfDropTarget, ShelfDragItem>()
    val cards = mutableStateMapOf<ShelfDropTarget, Rect>()
    val layouts = mutableStateMapOf<ShelfDropTarget, Rect>()
    val covers = mutableStateMapOf<ShelfDropTarget, Rect>()
    val menus = mutableStateMapOf<ShelfDropTarget, Rect>()
    val openMenus = mutableStateMapOf<ShelfDropTarget, Boolean>()
    var session by mutableStateOf<ShelfDragSession?>(null)
    var returnProgress by mutableFloatStateOf(0f)
    var origin by mutableStateOf(Offset.Zero)
    var viewport by mutableStateOf(Rect.Zero)
    var importButton by mutableStateOf(Rect.Zero)
    var enabled by mutableStateOf(true)

    fun cancel() { session = null; returnProgress = 0f }
    fun returnToSource() {
        val current = session ?: return
        returnProgress = 0f
        session = current.copy(dragging = false, returning = true)
    }
    fun targetAt(point: Offset): ShelfDragItem? {
        val current = session ?: return null
        if (!current.dragging || !viewport.contains(point) || importButton.contains(point)) return null
        return items.values.firstOrNull { item ->
            item.target != ShelfDropTarget.Book(current.book.id) &&
                covers[item.target]?.contains(point) == true && menus[item.target]?.contains(point) != true
        }
    }
    val target: ShelfDragItem? get() = session?.let { targetAt(it.position) }
}

@Composable
internal fun rememberShelfDragState(
    grid: LazyGridState,
    enabled: Boolean,
    shelfKey: Any,
): ShelfDragState {
    val drag = remember(grid) { ShelfDragState(grid) }
    SideEffect { drag.enabled = enabled }
    LaunchedEffect(enabled, shelfKey) { drag.cancel() }
    BackHandler(drag.session != null) { drag.cancel() }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) drag.cancel()
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer); drag.cancel() }
    }
    LaunchedEffect(drag.session?.returning) {
        val returning = drag.session?.takeIf { it.returning } ?: return@LaunchedEffect
        try {
            val source = drag.layouts[ShelfDropTarget.Book(returning.book.id)]
            if (source == null || !drag.viewport.contains(source.topLeft) ||
                !drag.viewport.contains(source.bottomRight - Offset(1f, 1f))) {
                // A recycled source needs to be revealed before the preview can settle back into its slot.
                grid.animateScrollToItem(returning.scrollIndex, returning.scrollOffset)
                withFrameNanos { }
            }
            animate(0f, 1f, animationSpec = tween(280, easing = FastOutSlowInEasing)) { value, _ ->
                drag.returnProgress = value
            }
        } finally {
            if (drag.session === returning) drag.cancel()
        }
    }
    val density = LocalDensity.current
    val edge = with(density) { 48.dp.toPx() }
    val maxSpeed = with(density) { 600.dp.toPx() }
    LaunchedEffect(drag.session?.book?.id, drag.session?.dragging) {
        var lastFrame = 0L
        while (isActive && drag.session?.dragging == true) {
            val frame = withFrameNanos { it }
            val delta = if (lastFrame == 0L) 0f else ((frame - lastFrame) / 1_000_000_000f).coerceAtMost(.05f)
            lastFrame = frame
            val session = drag.session ?: break
            if (!session.dragging || !drag.viewport.contains(session.position)) continue
            val velocity = shelfEdgeVelocity(session.position.y, drag.viewport.top, drag.viewport.bottom, edge, maxSpeed)
            if (velocity != 0f) grid.scrollBy(velocity * delta)
        }
    }
    return drag
}

internal fun shelfEdgeVelocity(y: Float, top: Float, bottom: Float, edge: Float, maxSpeed: Float): Float = when {
    y < top || y > bottom || bottom <= top -> 0f
    y < top + edge -> -maxSpeed * ((top + edge - y) / edge).coerceIn(0f, 1f)
    y > bottom - edge -> maxSpeed * ((y - bottom + edge) / edge).coerceIn(0f, 1f)
    else -> 0f
}

/** Unlike the generic long-press detector, a second pointer always cancels this gesture. */
private suspend fun AwaitPointerEventScope.awaitShelfLongPress(down: PointerInputChange): PointerInputChange? {
    var latest = down
    val interrupted = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
        while (true) {
            val event = awaitPointerEvent()
            if (event.changes.size != 1) return@withTimeoutOrNull true
            val change = event.changes.single()
            if (change.id != down.id || !change.pressed || change.isConsumed ||
                (change.position - down.position).getDistance() > viewConfiguration.touchSlop) return@withTimeoutOrNull true
            latest = change
            if (awaitPointerEvent(PointerEventPass.Final).changes.any { it.isConsumed }) return@withTimeoutOrNull true
        }
    }
    return if (interrupted == null) latest else null
}

@Composable
internal fun Modifier.shelfDragContainer(drag: ShelfDragState, onDrop: (String, ShelfDropTarget) -> Unit): Modifier {
    val haptics = LocalHapticFeedback.current
    val drop by rememberUpdatedState(onDrop)
    return onGloballyPositioned {
        drag.origin = it.positionInRoot()
        drag.viewport = it.boundsInRoot()
    }.pointerInput(drag) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val point = down.position + drag.origin
            if (!drag.enabled || drag.session?.returning == true || drag.openMenus.values.any { it } || drag.importButton.contains(point)) return@awaitEachGesture
            val source = drag.items.values.firstOrNull {
                it.book != null && drag.cards[it.target]?.contains(point) == true && drag.menus[it.target]?.contains(point) != true
            } ?: return@awaitEachGesture
            val pressed = awaitShelfLongPress(down) ?: return@awaitEachGesture
            if (!drag.enabled || drag.openMenus.values.any { it }) return@awaitEachGesture
            val rect = drag.cards[source.target] ?: return@awaitEachGesture
            val start = pressed.position + drag.origin
            drag.session = ShelfDragSession(
                source.book!!, start, start, start - rect.topLeft, rect.width,
                originalTopLeft = drag.layouts[source.target]?.topLeft ?: rect.topLeft,
                scrollIndex = drag.grid?.firstVisibleItemIndex ?: 0,
                scrollOffset = drag.grid?.firstVisibleItemScrollOffset ?: 0,
            )
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            try {
                while (true) {
                    // Consume before child click handlers, including the first up after a stationary long press.
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    if (event.changes.count { it.pressed } > 1 || event.changes.any { it.id != down.id && it.pressed }) {
                        drag.cancel()
                    }
                    val change = event.changes.find { it.id == down.id }
                    val previous = drag.session
                    if (change != null && previous != null) {
                        val position = change.position + drag.origin
                        drag.session = previous.copy(
                            position = position,
                            dragging = previous.dragging || (position - previous.initialPosition).getDistance() > viewConfiguration.touchSlop,
                        )
                    }
                    val consumedElsewhere = event.changes.any { it.isConsumed }
                    event.changes.forEach { it.consume() }
                    if (consumedElsewhere) drag.cancel()
                    if (event.changes.none { it.pressed }) {
                        val session = drag.session
                        val target = drag.target
                        if (session != null && target != null && drag.enabled) {
                            drag.cancel()
                            drop(session.book.id, target.target)
                        } else if (session != null && drag.enabled) {
                            drag.returnToSource()
                        } else {
                            drag.cancel()
                        }
                        break
                    }
                }
            } finally {
                if (drag.session?.returning != true) drag.cancel()
            }
        }
    }
}

@Composable
internal fun Modifier.shelfDragCard(drag: ShelfDragState, item: ShelfDragItem): Modifier {
    DisposableEffect(drag, item.target) {
        onDispose {
            drag.items.remove(item.target); drag.cards.remove(item.target); drag.layouts.remove(item.target); drag.covers.remove(item.target)
            drag.menus.remove(item.target); drag.openMenus.remove(item.target)
        }
    }
    SideEffect { drag.items[item.target] = item }
    return onGloballyPositioned {
        drag.cards[item.target] = it.boundsInRoot()
        drag.layouts[item.target] = Rect(it.positionInRoot(), it.size.toSize())
    }.graphicsLayer {
        val source = drag.session?.book?.id == item.book?.id && item.book != null
        val settle = ((drag.returnProgress - .85f) / .15f).coerceIn(0f, 1f)
        alpha = if (source) .35f + .65f * settle else 1f
    }
}

@Composable
internal fun Modifier.shelfDropCover(drag: ShelfDragState, target: ShelfDropTarget): Modifier {
    val active = drag.target?.target == target
    return onGloballyPositioned { drag.covers[target] = it.boundsInRoot() }
        .then(if (active) Modifier.border(3.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(10.dp)) else Modifier)
}

@Composable
internal fun BoxScope.ShelfDragOverlay(drag: ShelfDragState) {
    val session = drag.session ?: return
    val density = LocalDensity.current
    val progress = if (session.returning) drag.returnProgress else 0f
    val settle = ((progress - .85f) / .15f).coerceIn(0f, 1f)
    val previewScale = .82f + .18f * progress
    val from = session.position - session.grabOffset * .82f
    val destination = drag.layouts[ShelfDropTarget.Book(session.book.id)]?.topLeft ?: session.originalTopLeft
    val position = (if (session.returning) lerp(from, destination, progress) else from) - drag.origin
    if (session.returning) {
        Box(Modifier.matchParentSize().pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
            }
        })
    }
    Column(Modifier.offset { IntOffset(position.x.roundToInt(), position.y.roundToInt()) }
        .width(with(density) { session.width.toDp() }).graphicsLayer {
            alpha = .94f * (1f - settle)
            scaleX = previewScale
            scaleY = previewScale
            transformOrigin = TransformOrigin(0f, 0f)
            shape = RoundedCornerShape(10.dp)
            clip = true
            shadowElevation = 12.dp.toPx() * (1f - progress)
        }
        .background(MaterialTheme.colorScheme.surface)
        .testTag(if (session.returning) "shelf-return-preview" else "shelf-drag-preview")) {
        Box(Modifier.fillMaxWidth().height(166.dp)) {
            val book = session.book
            if (book.coverPath != null) AsyncImage(File(book.coverPath), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            else GeneratedBookCover(book.title, Modifier.fillMaxSize())
        }
        Text(session.book.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
    if (session.returning) return
    val target = drag.target
    Surface(Modifier.align(androidx.compose.ui.Alignment.BottomCenter).padding(12.dp).testTag("shelf-drag-hint"), shape = RoundedCornerShape(12.dp), tonalElevation = 6.dp) {
        Text(when (target?.target) {
            is ShelfDropTarget.Group -> "移入『${target.name}』"
            is ShelfDropTarget.Book -> "与『${target.name}』创建分类"
            null -> "拖到分类中，或与另一本书创建分类"
        }, Modifier.padding(12.dp), maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}
