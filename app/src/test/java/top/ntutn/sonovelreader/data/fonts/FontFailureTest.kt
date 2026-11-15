package top.ntutn.sonovelreader.data.fonts

import java.io.EOFException
import java.io.IOException
import java.util.concurrent.CancellationException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FontFailureTest {
    @Test fun permanentFailuresMayRepairSelection() {
        for (code in listOf(FontError.NOT_FOUND, FontError.CORRUPT, FontError.UNSUPPORTED, FontError.TOO_LARGE)) {
            assertTrue(FontException(code).isPermanentSelectionFailure)
        }
        assertTrue(FontException(FontError.LOAD_FAILED, IllegalArgumentException()).isPermanentSelectionFailure)
        assertTrue(FontException(FontError.LOAD_FAILED, EOFException()).isPermanentSelectionFailure)
    }

    @Test fun transientIoSecurityAndCatalogFailuresMustNotOverwriteSelection() {
        for (cause in listOf(IOException("EIO"), SecurityException(), CancellationException())) {
            val wrapped = IllegalStateException("wrapper", cause)
            assertFalse(FontException(FontError.LOAD_FAILED, wrapped).isPermanentSelectionFailure)
        }
        for (code in listOf(FontError.CATALOG_READ, FontError.CATALOG_WRITE, FontError.NO_SPACE, FontError.SOURCE_UNREADABLE)) {
            assertFalse(FontException(code).isPermanentSelectionFailure)
        }
    }

    @Test fun cyclicCauseChainCannotHangFailureClassification() {
        val first = IllegalStateException()
        val second = IOException()
        first.initCause(second)
        second.initCause(first)
        assertFalse(FontException(FontError.LOAD_FAILED, first).isPermanentSelectionFailure)
    }
}
