package top.ntutn.sonovelreader.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.math.abs
import kotlin.math.min

internal enum class ChapterPullEdge { NONE, PREVIOUS, NEXT }

internal class ChapterPullState(
    private val thresholdPx: Float,
    private val maxDistancePx: Float,
    private val resistance: Float = DEFAULT_RESISTANCE,
) {
    var edge by mutableStateOf(ChapterPullEdge.NONE)
        private set

    var distancePx by mutableFloatStateOf(0f)
        private set

    val isActive: Boolean get() = edge != ChapterPullEdge.NONE && distancePx > 0f
    val isArmed: Boolean get() = isActive && distancePx >= thresholdPx
    val progress: Float get() = (distancePx / thresholdPx).coerceIn(0f, 1f)
    val signedDistancePx: Float get() = when (edge) {
        ChapterPullEdge.PREVIOUS -> distancePx
        ChapterPullEdge.NEXT -> -distancePx
        ChapterPullEdge.NONE -> 0f
    }

    fun consumePostScroll(
        deltaY: Float,
        atStart: Boolean,
        atEnd: Boolean,
        hasPreviousChapter: Boolean,
        hasNextChapter: Boolean,
        isUserInput: Boolean,
    ): Float {
        if (!isUserInput || deltaY == 0f) return 0f
        val targetEdge = when {
            deltaY > 0f && atStart && hasPreviousChapter -> ChapterPullEdge.PREVIOUS
            deltaY < 0f && atEnd && hasNextChapter -> ChapterPullEdge.NEXT
            else -> return 0f
        }
        if (edge != ChapterPullEdge.NONE && edge != targetEdge) reset()
        edge = targetEdge
        distancePx = (distancePx + abs(deltaY) * resistance).coerceAtMost(maxDistancePx)
        return deltaY
    }

    fun consumePreScroll(deltaY: Float, isUserInput: Boolean): Float {
        if (!isUserInput || !isActive || deltaY == 0f) return 0f
        val reversesPull = when (edge) {
            ChapterPullEdge.PREVIOUS -> deltaY < 0f
            ChapterPullEdge.NEXT -> deltaY > 0f
            ChapterPullEdge.NONE -> false
        }
        if (!reversesPull) return 0f

        val rawDistanceToZero = distancePx / resistance
        val consumedMagnitude = min(abs(deltaY), rawDistanceToZero)
        distancePx = (distancePx - consumedMagnitude * resistance).coerceAtLeast(0f)
        if (distancePx <= ZERO_TOLERANCE_PX) reset()
        return if (deltaY > 0f) consumedMagnitude else -consumedMagnitude
    }

    fun release(): ChapterPullEdge {
        val result = edge.takeIf { isArmed } ?: ChapterPullEdge.NONE
        reset()
        return result
    }

    fun reset() {
        edge = ChapterPullEdge.NONE
        distancePx = 0f
    }

    private companion object {
        const val DEFAULT_RESISTANCE = 0.5f
        const val ZERO_TOLERANCE_PX = 0.5f
    }
}
