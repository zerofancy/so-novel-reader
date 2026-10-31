package top.ntutn.sonovelreader

import org.junit.Assert.*
import org.junit.Test
import top.ntutn.sonovelreader.data.ReadingMode
import top.ntutn.sonovelreader.tts.TtsPlaybackStatus
import top.ntutn.sonovelreader.ui.volumePagingAllowed

class VolumeKeyRouterTest {
    @Test fun longPressTurnsOnceAndKeepsItsOwnerUntilUp() {
        val router = VolumeKeyRouter()
        var turns = 0
        assertTrue(router.down(1, 0, false) { turns++; true })
        repeat(10) { assertTrue(router.down(1, it + 1, false) { fail("Repeat cannot turn"); false }) }
        assertTrue(router.up(1))
        assertFalse(router.up(1))
        assertEquals(1, turns)
    }

    @Test fun systemPressCannotBeTakenOverAfterEligibilityChanges() {
        val router = VolumeKeyRouter()
        assertFalse(router.down(1, 0, false) { false })
        assertFalse(router.down(1, 1, false) { fail("No mid-press takeover"); true })
        assertFalse(router.up(1))
        assertTrue(router.down(1, 0, false) { true })
    }

    @Test fun overlappingKeysNeverTurnTwice() {
        val router = VolumeKeyRouter()
        assertTrue(router.down(1, 0, false) { true })
        assertTrue(router.down(2, 0, false) { fail("Overlapping key"); false })
        assertTrue(router.up(1))
        assertTrue(router.down(2, 1, false) { fail("Still overlapping press"); false })
        assertTrue(router.up(2))
    }

    @Test fun overlappingSystemKeysStayWithSystem() {
        val router = VolumeKeyRouter()
        assertFalse(router.down(1, 0, false) { false })
        assertFalse(router.down(2, 0, false) { fail("Cannot take over chord"); true })
        assertFalse(router.up(1))
        assertFalse(router.up(2))
    }

    @Test fun canceledAndOrphanedEventsDoNotTurn() {
        val router = VolumeKeyRouter()
        assertFalse(router.down(1, 0, true) { fail("Canceled"); true })
        assertFalse(router.down(1, 2, false) { fail("Orphan repeat"); true })
        assertFalse(router.up(1))
        assertTrue(router.down(1, 0, false) { true })
        assertTrue(router.down(1, 1, true) { fail("Canceled repeat"); false })
        assertTrue(router.up(1))
    }

    @Test fun allModesAndPlaybackStatesMatchSpec() {
        for (mode in ReadingMode.entries) for (status in TtsPlaybackStatus.entries) {
            val expected = mode in listOf(ReadingMode.PAGED, ReadingMode.FLIP) &&
                status in listOf(TtsPlaybackStatus.IDLE, TtsPlaybackStatus.ERROR, TtsPlaybackStatus.COMPLETED)
            assertEquals("$mode / $status", expected, volumePagingAllowed(mode, status))
        }
    }
}
