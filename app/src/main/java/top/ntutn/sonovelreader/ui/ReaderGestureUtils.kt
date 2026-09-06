package top.ntutn.sonovelreader.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputScope
import kotlin.math.abs

internal suspend fun PointerInputScope.detectHorizontalReaderGestures(
    currentPage: () -> Int,
    lastPage: Int,
    onTap: (Offset) -> Unit,
    onSwipePastStart: () -> Unit,
    onSwipePastEnd: () -> Unit,
    onUserSwipe: () -> Unit,
) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val startPosition = down.position
        val startPage = currentPage()
        var endPosition = startPosition
        var pressed = true
        do {
            val event = awaitPointerEvent(PointerEventPass.Final)
            val change = event.changes.firstOrNull { it.id == down.id } ?: break
            endPosition = change.position
            pressed = change.pressed
        } while (pressed)
        val delta = endPosition - startPosition
        val threshold = viewConfiguration.touchSlop * 3
        if (abs(delta.x) > viewConfiguration.touchSlop || abs(delta.y) > viewConfiguration.touchSlop) onUserSwipe()
        when {
            abs(delta.x) < viewConfiguration.touchSlop && abs(delta.y) < viewConfiguration.touchSlop -> onTap(endPosition)
            abs(delta.x) > threshold && abs(delta.x) > abs(delta.y) && delta.x > 0 && startPage == 0 -> onSwipePastStart()
            abs(delta.x) > threshold && abs(delta.x) > abs(delta.y) && delta.x < 0 && startPage == lastPage -> onSwipePastEnd()
        }
    }
}
