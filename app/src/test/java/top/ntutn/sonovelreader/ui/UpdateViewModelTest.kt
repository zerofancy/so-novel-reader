package top.ntutn.sonovelreader.ui

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import top.ntutn.updater.*
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import org.junit.Test
import org.junit.Assert.*
import top.ntutn.sonovelreader.R
import top.ntutn.sonovelreader.data.update.*

@OptIn(ExperimentalCoroutinesApi::class)
class UpdateViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val installed = InstalledAppInfo("1.0", 1, "ARM64", false)
    private val latest = UpdateCheckResult.Available(testRelease())
    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun repeatedClicksMakeOneCallAndShowDetailsOnlyOnce() = runTest(dispatcher) {
        var calls = 0
        val pending = CompletableDeferred<UpdateCheckResult>()
        val vm = UpdateViewModel(installed) { calls++; pending.await() }
        vm.setVisible(true)
        vm.checkUpdates(); vm.checkUpdates(); runCurrent()
        assertEquals(1, calls); assertTrue(vm.state.value.checking)
        pending.complete(latest); runCurrent()
        assertTrue(vm.state.value.showDetails)
        vm.dismissDetails(); vm.setVisible(false); vm.setVisible(true)
        assertFalse(vm.state.value.showDetails)
        assertEquals(latest.release, vm.state.value.available)
        vm.showDetails(); assertTrue(vm.state.value.showDetails)
    }

    @Test fun backgroundCompletionKeepsResultWithoutOpeningDialogOnReturn() = runTest(dispatcher) {
        val pending = CompletableDeferred<UpdateCheckResult>()
        val vm = UpdateViewModel(installed) { pending.await() }
        vm.setVisible(true); vm.checkUpdates(); runCurrent(); vm.setVisible(false)
        pending.complete(latest); runCurrent(); vm.setVisible(true)
        assertFalse(vm.state.value.showDetails)
        assertEquals(latest.release, vm.state.value.available)
    }

    @Test fun newCheckClearsOldDownloadAndFailureDoesNotClaimCurrent() = runTest(dispatcher) {
        var calls = 0
        val vm = UpdateViewModel(installed) {
            if (++calls == 1) latest else UpdateCheckResult.Failed(UpdateError(UpdateErrorKind.ServiceConfiguration, 404, "SOFTWARE_NOT_FOUND"))
        }
        vm.checkUpdates(); runCurrent(); assertNotNull(vm.state.value.available)
        vm.checkUpdates(); assertNull(vm.state.value.available); runCurrent()
        assertEquals(R.string.update_not_configured, vm.state.value.message)
        assertFalse(vm.state.value.checking)
    }

    @Test fun equalAndLowerVersionsHaveNoDownload() = runTest(dispatcher) {
        for ((version, message) in listOf(2L to R.string.update_current, 1L to R.string.update_ahead)) {
            val vm = UpdateViewModel(installed.copy(versionCode = 2)) { UpdateCheckResult.UpToDate(UpdateVersion(version, "1.0")) }
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
        val vm = UpdateViewModel(installed) { calls++; UpdateCheckResult.Failed(UpdateError(UpdateErrorKind.ServiceUnavailable, 429, retryAt = Clock.System.now() + 3.seconds)) }
        vm.checkUpdates(); runCurrent(); assertTrue(vm.state.value.retryBlocked)
        vm.checkUpdates(); assertEquals(1, calls)
        advanceTimeBy(3001); runCurrent(); assertFalse(vm.state.value.retryBlocked)
    }
}

private fun testRelease() = UpdateRelease(
    "so-novel-reader", UpdateTarget(UpdateOs.Android, UpdateArch.ARM64), "apk",
    UpdateVersion(2, "1.1"), "https://example.com/app.apk", "拾光阅读", "测试发布", "apk",
    null, null, null, 1024, "更新说明", null,
)
