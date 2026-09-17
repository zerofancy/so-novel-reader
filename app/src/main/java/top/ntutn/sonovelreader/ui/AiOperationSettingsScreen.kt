package top.ntutn.sonovelreader.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import top.ntutn.sonovelreader.AppContainer

/**
 * AI 操作设置页：允许系统 AI 助手（GUI Agent）操作本应用的总开关，
 * 以及更细粒度的删除权限。变更会立即同步为 SAEP 动态策略。
 *
 * 页面用 Scaffold 包裹，确保背景与文字颜色跟随系统深浅色主题。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiOperationSettingsScreen(
    container: AppContainer,
    onBack: () -> Unit,
) {
    val viewModel: AiOperationSettingsViewModel = viewModel(factory = AppViewModelFactory(container))
    val settings by viewModel.aiSettings.collectAsStateWithLifecycle()
    val saepAvailable by viewModel.saepAvailable.collectAsStateWithLifecycle()
    val syncState by viewModel.syncState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AI 操作设置", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { contentPadding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (saepAvailable == false) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                ) {
                    Text(
                        "当前设备不支持 AI 操作协议（需 ObricUI 2.2 及以上系统）。" +
                            "以下设置会保存，并在支持的设备上生效。",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(12.dp),
                    )
                }
            }

            AiSwitchRow(
                title = "允许 AI 操作",
                subtitle = "允许系统 AI 助手操作本应用（阅读、翻页等）",
                checked = settings.allowAiOperation,
                onCheckedChange = viewModel::setAllowAiOperation,
                enabled = true,
            )
            HorizontalDivider()
            AiSwitchRow(
                title = "允许 AI 删除内容",
                subtitle = "允许 AI 删除书架中的书籍、分组等内容",
                checked = settings.allowAiDeleteContent,
                onCheckedChange = viewModel::setAllowAiDeleteContent,
                enabled = settings.allowAiOperation,
            )
            HorizontalDivider()

            when (val state = syncState) {
                is SaepSyncState.Synced -> Text(
                    "策略已同步（v${state.version}）",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                SaepSyncState.Failed -> Text(
                    "策略同步失败，当前设置仅保存在本机",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
                SaepSyncState.Idle -> Unit
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun AiSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                fontWeight = FontWeight.Medium,
                color = if (enabled) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
        )
    }
}
