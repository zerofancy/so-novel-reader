package top.ntutn.sonovelreader.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import top.ntutn.sonovelreader.data.ReaderSettings

// Only explicit reading styles consume this local; Material/UI typography is unchanged.
internal val LocalReaderFont = staticCompositionLocalOf<FontFamily> { FontFamily.Default }

@Composable
internal fun readerChapterTitleStyle(settings: ReaderSettings, color: Color) =
    readerTextStyle(settings, color, applyIndent = false).copy(
        fontSize = (settings.fontSizeSp * 1.3f).sp,
        lineHeight = (settings.fontSizeSp * 1.3f * settings.lineHeight).sp,
        fontWeight = FontWeight.Bold,
    )
