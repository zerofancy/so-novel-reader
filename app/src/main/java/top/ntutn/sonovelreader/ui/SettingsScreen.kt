package top.ntutn.sonovelreader.ui

import android.content.Intent
import android.speech.tts.TextToSpeech
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.ui.draw.clip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import top.ntutn.sonovelreader.data.ReaderSettings
import top.ntutn.sonovelreader.data.ReaderTheme
import top.ntutn.sonovelreader.data.ReadingMode
import top.ntutn.sonovelreader.tts.TtsVoiceCatalogState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: ReaderSettings,
    onModeChange: (ReadingMode) -> Unit,
    onFontSizeChange: (Int) -> Unit,
    onLineHeightChange: (Float) -> Unit,
    onFirstLineIndentChange: (Boolean) -> Unit,
    onParagraphSpacingChange: (Int) -> Unit,
    onThemeChange: (ReaderTheme) -> Unit,
    onKeepScreenOnChange: (Boolean) -> Unit,
    ttsVoices: TtsVoiceCatalogState,
    onTtsRateChange: (Float) -> Unit,
    onTtsPitchChange: (Float) -> Unit,
    onTtsVoiceChange: (String?) -> Unit,
    showAiOperationEntry: Boolean,
    aiAllowOperation: Boolean,
    onOpenAiOperationSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var rate by remember(settings.ttsRate) { mutableFloatStateOf(settings.ttsRate) }
    var pitch by remember(settings.ttsPitch) { mutableFloatStateOf(settings.ttsPitch) }
    var voiceMenuExpanded by remember { mutableStateOf(false) }
    Column(modifier.fillMaxSize()) {
        TopAppBar(title = { Text("阅读设置", fontWeight = FontWeight.SemiBold) })
        if (showAiOperationEntry) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClick = onOpenAiOperationSettings)
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Default.SmartToy,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("AI 操作设置", fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(2.dp))
                    Text(
                        if (aiAllowOperation) "允许 AI 助手操作本应用" else "已禁止 AI 助手操作本应用",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            HorizontalDivider()
        }
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
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
                FilterChip(
                    selected = settings.readingMode == ReadingMode.FLIP,
                    onClick = { onModeChange(ReadingMode.FLIP) },
                    label = { Text("仿真翻页") },
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
            Row(
                Modifier.fillMaxWidth().padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("首行缩进", fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "首行缩进两个汉字宽度",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = settings.firstLineIndent, onCheckedChange = onFirstLineIndentChange)
            }
            HorizontalDivider()
            SettingTitle("段间距  ${settings.paragraphSpacingDp} dp")
            Slider(
                value = settings.paragraphSpacingDp.toFloat(),
                onValueChange = { onParagraphSpacingChange(it.roundToInt()) },
                valueRange = 4f..48f,
                steps = 21,
            )
            HorizontalDivider()
            SettingTitle("阅读主题")
            FlowRow(
                modifier = Modifier.fillMaxWidth().selectableGroup(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                ThemeChoice("跟随系统", ReaderTheme.SYSTEM, settings.theme, onThemeChange)
                ThemeChoice("浅色", ReaderTheme.LIGHT, settings.theme, onThemeChange)
                ThemeChoice("深色", ReaderTheme.DARK, settings.theme, onThemeChange)
                ThemeChoice("米黄纸", ReaderTheme.SEPIA, settings.theme, onThemeChange)
                ThemeChoice("亚麻纸", ReaderTheme.LINEN, settings.theme, onThemeChange)
                ThemeChoice("淡绿", ReaderTheme.GREEN, settings.theme, onThemeChange)
                ThemeChoice("柔灰", ReaderTheme.GRAY, settings.theme, onThemeChange)
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
            HorizontalDivider()
            SettingTitle("朗读语速  ${"%.1f".format(rate)}×")
            Slider(
                value = rate,
                onValueChange = { rate = (it * 10).roundToInt() / 10f },
                onValueChangeFinished = { onTtsRateChange(rate) },
                valueRange = 0.5f..2f,
                steps = 14,
            )
            SettingTitle("朗读音调  ${"%.1f".format(pitch)}×")
            Slider(
                value = pitch,
                onValueChange = { pitch = (it * 10).roundToInt() / 10f },
                onValueChangeFinished = { onTtsPitchChange(pitch) },
                valueRange = 0.5f..2f,
                steps = 14,
            )
            SettingTitle("系统语音")
            Box {
                val selectedVoice = ttsVoices.voices.firstOrNull { it.name == settings.ttsVoiceName }
                OutlinedButton(
                    onClick = { voiceMenuExpanded = true },
                    enabled = !ttsVoices.loading && ttsVoices.voices.isNotEmpty(),
                ) {
                    Text(
                        when {
                            ttsVoices.loading -> "正在读取系统语音…"
                            selectedVoice != null -> selectedVoice.label
                            settings.ttsVoiceName != null -> "语音已不可用 · 自动匹配"
                            else -> "自动匹配（推荐）"
                        },
                    )
                }
                DropdownMenu(expanded = voiceMenuExpanded, onDismissRequest = { voiceMenuExpanded = false }) {
                    DropdownMenuItem(
                        text = { Text("自动匹配（推荐）") },
                        onClick = {
                            onTtsVoiceChange(null)
                            voiceMenuExpanded = false
                        },
                    )
                    ttsVoices.voices.forEach { voice ->
                        DropdownMenuItem(
                            text = { Text(voice.label) },
                            onClick = {
                                onTtsVoiceChange(voice.name)
                                voiceMenuExpanded = false
                            },
                        )
                    }
                }
            }
            ttsVoices.error?.let { error ->
                Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                OutlinedButton(
                    onClick = { context.startActivity(Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA)) },
                ) { Text("安装系统语音数据") }
            }
            Spacer(Modifier.height(24.dp))
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
    val palette = readerPalette(value)
    val isSelected = value == selected
    Surface(
        modifier = Modifier.width(140.dp),
        shape = RoundedCornerShape(12.dp),
        color = palette.background,
        contentColor = palette.foreground,
        border = BorderStroke(if (isSelected) 2.dp else 1.dp, if (isSelected) palette.foreground else palette.muted),
    ) {
        Column(
            modifier = Modifier
                .testTag("reader-theme-${value.name}")
                .selectable(selected = isSelected, role = Role.RadioButton, onClick = { onSelected(value) })
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
                if (isSelected) {
                    Icon(Icons.Default.Check, contentDescription = null)
                }
            }
            Text("春风拂过书页", style = MaterialTheme.typography.bodyMedium)
            Text("阅读配色", color = palette.muted, style = MaterialTheme.typography.labelSmall)
        }
    }
}
