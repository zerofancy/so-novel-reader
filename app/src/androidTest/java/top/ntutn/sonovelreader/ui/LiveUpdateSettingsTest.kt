package top.ntutn.sonovelreader.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import top.ntutn.sonovelreader.data.update.InstalledAppInfo
import top.ntutn.sonovelreader.data.update.createUpdateHttpClient
import top.ntutn.updater.*
import kotlin.time.Duration.Companion.seconds

/** Opt in with -Pandroid.testInstrumentationRunnerArguments.liveUpdates=true. Reads public test records only. */
class LiveUpdateSettingsTest {
    @get:Rule val composeRule = createComposeRule()

    @Before fun requireOptIn() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("liveUpdates") == "true")
    }

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun config(version: Long, packageType: String = "apk"): UpdaterConfig {
        val installed = InstalledAppInfo.read(context)
        assertEquals("ARM64", installed.arch)
        return UpdaterConfig(
            baseUrl = "https://zp.ntutn.top",
            software = "so-novel-reader-update-test",
            currentVersion = UpdateVersion(version, "live-test.$version"),
            target = UpdateTarget(UpdateOs.Android, UpdateArch.valueOf(requireNotNull(installed.arch))),
            packageType = packageType,
            requestTimeout = 25.seconds,
        )
    }

    @Test fun realServiceShowsUpdateAndDispatchesBrowser() {
        // The device supplies its real ABI. Version 1 represents the older installation without downgrading it.
        val installed = InstalledAppInfo.read(context).copy(versionCode = 1, versionName = "live-test.1", debug = false)
        createUpdateHttpClient().use { client ->
            val checker = createUpdateChecker(config(1), client)
            try {
                val vm = UpdateViewModel(installed) { checker.check() }
                composeRule.setContent { MaterialTheme { UpdateSettingsSection(vm) } }
                composeRule.onNodeWithText("检查更新").performClick()
                composeRule.waitUntil(30_000) { vm.state.value.available != null || !vm.state.value.checking }
                val release = requireNotNull(vm.state.value.available) { "Live query failed: ${vm.state.value.message}" }
                assertEquals(2, release.version.code.toInt())
                assertEquals("so-novel-reader-update-test", release.software)
                composeRule.onNodeWithText("新版本 ${release.version.name}（${release.version.code}）").assertExists()
                composeRule.onNodeWithText("稍后").performClick()
                composeRule.onNodeWithText("查看更新").performClick()
                composeRule.onNodeWithText("前往下载").performClick()
                composeRule.waitUntil(10_000) { !vm.state.value.showDetails }
            } finally { checker.close() }
        }
    }

    @Test fun realServiceDistinguishesCurrentAheadAndMissingPackage() = runBlocking {
        createUpdateHttpClient().use { client ->
            for (version in listOf(2L, 3L)) {
                val checker = createUpdateChecker(config(version), client)
                try {
                    val result = checker.check()
                    assertTrue("Expected UpToDate for $version: $result", result is UpdateCheckResult.UpToDate)
                    assertEquals(2L, (result as UpdateCheckResult.UpToDate).latest.code)
                } finally { checker.close() }
            }
            val checker = createUpdateChecker(config(1, "zip"), client)
            try { assertEquals(UpdateCheckResult.NoMatchingPackage, checker.check()) }
            finally { checker.close() }
        }
    }
}
