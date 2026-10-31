package top.ntutn.sonovelreader.ui

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import top.ntutn.sonovelreader.R
import top.ntutn.sonovelreader.data.update.*

@OptIn(ExperimentalCoroutinesApi::class)
class UpdateViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val installed = InstalledAppInfo("1.0", 1, "ARM64", false)
    private val latest = AvailableUpdate(2, "1.1", "发布", "https://example.com/app.apk", null)
    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun repeatedClicksMakeOneCallAndShowDetailsOnlyOnce() = runTest(dispatcher) {
        var calls = 0
        val pending = CompletableDeferred<AvailableUpdate>()
        val vm = UpdateViewModel(installed) { calls++; pending.await() }
        vm.setVisible(true)
        vm.checkUpdates(); vm.checkUpdates(); runCurrent()
        assertEquals(1, calls); assertTrue(vm.state.value.checking)
        pending.complete(latest); runCurrent()
        assertTrue(vm.state.value.showDetails)
        vm.dismissDetails(); vm.setVisible(false); vm.setVisible(true)
        assertFalse(vm.state.value.showDetails)
        assertEquals(latest, vm.state.value.available)
        vm.showDetails(); assertTrue(vm.state.value.showDetails)
    }

    @Test fun backgroundCompletionKeepsResultWithoutOpeningDialogOnReturn() = runTest(dispatcher) {
        val pending = CompletableDeferred<AvailableUpdate>()
        val vm = UpdateViewModel(installed) { pending.await() }
        vm.setVisible(true); vm.checkUpdates(); runCurrent(); vm.setVisible(false)
        pending.complete(latest); runCurrent(); vm.setVisible(true)
        assertFalse(vm.state.value.showDetails)
        assertEquals(latest, vm.state.value.available)
    }

    @Test fun newCheckClearsOldDownloadAndFailureDoesNotClaimCurrent() = runTest(dispatcher) {
        var calls = 0
        val vm = UpdateViewModel(installed) {
            if (++calls == 1) latest else throw UpdateServiceError(404, "SOFTWARE_NOT_FOUND")
        }
        vm.checkUpdates(); runCurrent(); assertNotNull(vm.state.value.available)
        vm.checkUpdates(); assertNull(vm.state.value.available); runCurrent()
        assertEquals(R.string.update_not_configured, vm.state.value.message)
        assertFalse(vm.state.value.checking)
    }

    @Test fun equalAndLowerVersionsHaveNoDownload() = runTest(dispatcher) {
        for ((version, message) in listOf(1L to R.string.update_current, 0L to R.string.update_ahead)) {
            val vm = UpdateViewModel(installed) { latest.copy(versionCode = version) }
            vm.checkUpdates(); runCurrent()
            assertEquals(message, vm.state.value.message); assertNull(vm.state.value.available)
        }
    }

    @Test fun debugAndUnsupportedInstallationsDoNotCallService() = runTest(dispatcher) {
        for (info in listOf(installed.copy(debug = true), installed.copy(arch = null))) {
            val vm = UpdateViewModel(info) { error("Must not request") }
            vm.checkUpdates(); runCurrent(); assertFalse(vm.supported); assertFalse(vm.state.value.checking)
        }
    }

    @Test fun rateLimitBlocksNewChecksUntilRetryAfter() = runTest(dispatcher) {
        var calls = 0
        val vm = UpdateViewModel(installed) { calls++; throw UpdateServiceError(429, null, 3000) }
        vm.checkUpdates(); runCurrent(); assertTrue(vm.state.value.retryBlocked)
        vm.checkUpdates(); assertEquals(1, calls)
        advanceTimeBy(3000); runCurrent(); assertFalse(vm.state.value.retryBlocked)
    }
}
