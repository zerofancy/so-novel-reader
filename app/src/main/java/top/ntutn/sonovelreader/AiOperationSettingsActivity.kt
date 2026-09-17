package top.ntutn.sonovelreader

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import top.ntutn.sonovelreader.ui.AiOperationSettingsScreen
import top.ntutn.sonovelreader.ui.theme.SoNovelReaderTheme

/**
 * AI 操作设置页。
 *
 * 该 Activity 在 SAEP 策略（AGRP-Policy/1.0）中声明为全局禁用
 * （scope.activities.AiOperationSettingsActivity.page_scope.global_disable=true），
 * GUI Agent 无法操作本页面，防止 AI 自行关闭权限限制。
 */
class AiOperationSettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SoNovelReaderTheme {
                AiOperationSettingsScreen(
                    container = (application as SoNovelReaderApplication).container,
                    onBack = ::finish,
                )
            }
        }
    }
}
