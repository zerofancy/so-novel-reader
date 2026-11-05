package top.ntutn.sonovelreader.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Rule
import top.ntutn.updater.*
import org.junit.Test
import top.ntutn.sonovelreader.data.update.InstalledAppInfo
import java.util.concurrent.atomic.AtomicInteger

class UpdateSettingsTest {
    @get:Rule val composeRule = createComposeRule()
    private val installed = InstalledAppInfo("1.0", 1, "ARM64", false)

    @Test fun manualCheckShowsDetailsAndKeepsResultAfterDismissal() {
        val calls = AtomicInteger()
        val pending = CompletableDeferred<UpdateCheckResult>()
        val vm = UpdateViewModel(installed) { calls.incrementAndGet(); pending.await() }
        composeRule.setContent { MaterialTheme { UpdateSettingsSection(vm) } }
        composeRule.onNodeWithText("当前版本 1.0（1）").assertExists()
        assertEquals(0, calls.get())
        composeRule.onNodeWithText("检查更新").performClick()
        composeRule.waitUntil { calls.get() == 1 }
        composeRule.onAllNodesWithText("正在检查更新…").assertCountEquals(2)
        composeRule.runOnIdle {
            pending.complete(UpdateCheckResult.Available(testRelease()))
        }
        composeRule.onNodeWithText("新版本 1.1（2）").assertExists()
        composeRule.onNodeWithText("前往下载").assertExists()
        composeRule.onNodeWithText("稍后").performClick()
        composeRule.onNodeWithText("查看更新").performClick()
        composeRule.onNodeWithText("新版本 1.1（2）").assertExists()
        assertEquals(1, calls.get())
    }

    @Test fun missingSoftwareIsNotReportedAsLatest() {
        val vm = UpdateViewModel(installed) { UpdateCheckResult.Failed(UpdateError(UpdateErrorKind.ServiceConfiguration, 404, "SOFTWARE_NOT_FOUND")) }
        composeRule.setContent { MaterialTheme { UpdateSettingsSection(vm) } }
        composeRule.onNodeWithText("检查更新").performClick()
        composeRule.onNodeWithText("更新服务尚未配置此应用。").assertExists()
        composeRule.onNodeWithText("当前已是最新版本").assertDoesNotExist()
        composeRule.onNodeWithText("前往下载").assertDoesNotExist()
    }

    @Test fun debugInstallationCannotRequestReleaseUpdates() {
        val vm = UpdateViewModel(installed.copy(debug = true)) { error("Must not request") }
        composeRule.setContent { MaterialTheme { UpdateSettingsSection(vm) } }
        composeRule.onNodeWithText("调试版本不支持应用内检查更新").assertExists()
        composeRule.onNodeWithText("检查更新").assertIsNotEnabled()
    }
}

private fun testRelease() = UpdateRelease(
    "so-novel-reader", UpdateTarget(UpdateOs.Android, UpdateArch.ARM64), "apk",
    UpdateVersion(2, "1.1"), "https://example.com/app.apk", "拾光阅读", "测试发布", "apk",
    null, null, null, 1024, "更新说明", null,
)
