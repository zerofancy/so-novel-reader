package top.ntutn.sonovelreader.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChapterPullStateTest {
    private fun state() = ChapterPullState(thresholdPx = 64f, maxDistancePx = 112f)

    @Test
    fun topPullArmsAndReleasesPreviousChapter() {
        val state = state()

        val consumed = state.consumePostScroll(
            deltaY = 160f,
            atStart = true,
            atEnd = false,
            hasPreviousChapter = true,
            hasNextChapter = true,
            isUserInput = true,
        )

        assertEquals(160f, consumed)
        assertEquals(ChapterPullEdge.PREVIOUS, state.edge)
        assertEquals(80f, state.distancePx)
        assertTrue(state.isArmed)
        assertEquals(ChapterPullEdge.PREVIOUS, state.release())
        assertFalse(state.isActive)
    }

    @Test
    fun pullBelowThresholdReturnsWithoutNavigation() {
        val state = state()
        state.consumePostScroll(100f, true, false, true, true, true)

        assertEquals(50f, state.distancePx)
        assertFalse(state.isArmed)
        assertEquals(ChapterPullEdge.NONE, state.release())
    }

    @Test
    fun reversePullDropsBelowThresholdAndCancelsActivation() {
        val state = state()
        state.consumePostScroll(160f, true, false, true, true, true)

        val consumed = state.consumePreScroll(deltaY = -40f, isUserInput = true)

        assertEquals(-40f, consumed)
        assertEquals(60f, state.distancePx)
        assertFalse(state.isArmed)
        assertEquals(ChapterPullEdge.NONE, state.release())
    }

    @Test
    fun reversePullConsumesOnlyDistanceNeededToReturnToZero() {
        val state = state()
        state.consumePostScroll(80f, true, false, true, true, true)

        val consumed = state.consumePreScroll(deltaY = -200f, isUserInput = true)

        assertEquals(-80f, consumed)
        assertFalse(state.isActive)
    }

    @Test
    fun bottomPullArmsNextChapterAndCapsDistance() {
        val state = state()
        state.consumePostScroll(-1_000f, false, true, true, true, true)

        assertEquals(ChapterPullEdge.NEXT, state.edge)
        assertEquals(112f, state.distancePx)
        assertEquals(-112f, state.signedDistancePx)
        assertEquals(1f, state.progress)
        assertEquals(ChapterPullEdge.NEXT, state.release())
    }

    @Test
    fun disabledBoundariesAndSideEffectsAreIgnored() {
        val state = state()

        assertEquals(0f, state.consumePostScroll(200f, true, false, false, true, true))
        assertEquals(0f, state.consumePostScroll(-200f, false, true, true, false, true))
        assertEquals(0f, state.consumePostScroll(200f, true, false, true, true, false))
        assertFalse(state.isActive)
    }
}
