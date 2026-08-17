package top.ntutn.sonovelreader.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import kotlin.math.max
import kotlin.math.min

private const val MinimumTextContrast = 4.5f

internal data class GeneratedCoverStyle(
    val label: String,
    val background: Color,
    val foreground: Color,
)

@Composable
internal fun GeneratedBookCover(title: String, modifier: Modifier = Modifier) {
    val style = remember(title) { generatedCoverStyle(title) }

    Box(
        modifier = modifier.background(style.background),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = style.label,
            color = style.foreground,
            fontSize = 52.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}

internal fun generatedCoverStyle(title: String): GeneratedCoverStyle {
    val normalizedTitle = title.trim()
    val hashBits = normalizedTitle.hashCode().toUInt()
    val hue = (hashBits % 360u).toFloat()
    val saturation = 0.42f + ((hashBits shr 9) % 17u).toFloat() / 100f
    val lightness = 0.36f + ((hashBits shr 17) % 13u).toFloat() / 100f
    val background = Color.hsl(hue, saturation, lightness)

    val lightCandidate = Color.hsl(hue, 0.18f, 0.94f)
    val darkCandidate = Color.hsl(hue, 0.22f, 0.10f)
    val foreground = highestContrastColor(background, lightCandidate, darkCandidate)
        .takeIf { contrastRatio(background, it) >= MinimumTextContrast }
        ?: highestContrastColor(background, Color.White, Color.Black)

    return GeneratedCoverStyle(
        label = firstCoverCharacter(title),
        background = background,
        foreground = foreground,
    )
}

internal fun firstCoverCharacter(title: String): String {
    var offset = 0
    var firstNonWhitespaceCodePoint: Int? = null

    while (offset < title.length) {
        val codePoint = Character.codePointAt(title, offset)
        if (!Character.isWhitespace(codePoint) && firstNonWhitespaceCodePoint == null) {
            firstNonWhitespaceCodePoint = codePoint
        }
        if (Character.UnicodeScript.of(codePoint) == Character.UnicodeScript.HAN) {
            return String(Character.toChars(codePoint))
        }
        offset += Character.charCount(codePoint)
    }

    return firstNonWhitespaceCodePoint?.let { String(Character.toChars(it)) } ?: "书"
}

internal fun contrastRatio(first: Color, second: Color): Float {
    val lighter = max(first.luminance(), second.luminance())
    val darker = min(first.luminance(), second.luminance())
    return (lighter + 0.05f) / (darker + 0.05f)
}

private fun highestContrastColor(background: Color, first: Color, second: Color): Color =
    if (contrastRatio(background, first) >= contrastRatio(background, second)) first else second
