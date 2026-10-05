package top.ntutn.sonovelreader.data

import org.junit.Assert.*
import org.junit.Test

class BookmarkNameTest {
    @Test fun validatesTrimmedUnicodeNames() {
        assertFalse(validBookmarkName("   "))
        assertTrue(validBookmarkName("  重要情节  "))
        assertTrue(validBookmarkName("😀".repeat(50)))
        assertFalse(validBookmarkName("😀".repeat(51)))
        assertFalse(validBookmarkName("字".repeat(51)))
    }
}
