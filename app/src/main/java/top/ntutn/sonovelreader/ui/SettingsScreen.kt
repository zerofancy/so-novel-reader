package top.ntutn.sonovelreader.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import top.ntutn.sonovelreader.data.ReaderSettings
import top.ntutn.sonovelreader.data.ReaderTheme
import top.ntutn.sonovelreader.data.ReadingMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: ReaderSettings,
    onModeChange: (ReadingMode) -> Unit,
    onFontSizeChange: (Int) -> Unit,
    onLineHeightChange: (Float) -> Unit,
    onThemeChange: (ReaderTheme) -> Unit,
    onKeepScreenOnChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize()) {
        TopAppBar(title = { Text("阅读设置", fontWeight = FontWeight.SemiBold) })
        Column(
            Modifier.fillMaxSize().padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SettingTitle("阅读方式")
            ChoiceRow {
                FilterChip(
                    selected = settings.readingMode == ReadingMode.SCROLL,
                    onClick = { onModeChange(ReadingMode.SCROLL) },
                    label = { Text("上下滚动") },
                )
                FilterChip(
                    selected = settings.readingMode == ReadingMode.PAGED,
                    onClick = { onModeChange(ReadingMode.PAGED) },
                    label = { Text("左右分页") },
                )
            }
            HorizontalDivider()
            SettingTitle("字号  ${settings.fontSizeSp} sp")
            Slider(
                value = settings.fontSizeSp.toFloat(),
                onValueChange = { onFontSizeChange(it.roundToInt()) },
                valueRange = 14f..32f,
                steps = 17,
            )
            SettingTitle("行距  ${"%.1f".format(settings.lineHeight)}")
            Slider(
                value = settings.lineHeight,
                onValueChange = { onLineHeightChange((it * 10).roundToInt() / 10f) },
                valueRange = 1.2f..2.2f,
                steps = 9,
            )
            HorizontalDivider()
            SettingTitle("阅读主题")
            ChoiceRow {
                ThemeChoice("跟随系统", ReaderTheme.SYSTEM, settings.theme, onThemeChange)
                ThemeChoice("浅色", ReaderTheme.LIGHT, settings.theme, onThemeChange)
            }
            ChoiceRow {
                ThemeChoice("深色", ReaderTheme.DARK, settings.theme, onThemeChange)
                ThemeChoice("护眼", ReaderTheme.SEPIA, settings.theme, onThemeChange)
            }
            HorizontalDivider()
            Row(
                Modifier.fillMaxWidth().padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("保持屏幕常亮", fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "仅在阅读界面生效",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = settings.keepScreenOn, onCheckedChange = onKeepScreenOnChange)
            }
        }
    }
}

@Composable
private fun SettingTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
}

@Composable
private fun ChoiceRow(content: @Composable RowScope.() -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        content = content,
    )
}

@Composable
private fun ThemeChoice(
    label: String,
    value: ReaderTheme,
    selected: ReaderTheme,
    onSelected: (ReaderTheme) -> Unit,
) {
    FilterChip(
        selected = value == selected,
        onClick = { onSelected(value) },
        label = { Text(label) },
    )
}
