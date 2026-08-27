package top.ntutn.sonovelreader.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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

/**
 * 用 Canvas 绘制与 [GeneratedBookCover] 视觉一致的封面 Bitmap，
 * 供 Service / 通知 / MediaSession 等非 Compose 场景使用。
 */
internal fun generatedCoverBitmap(title: String, widthPx: Int, heightPx: Int): Bitmap {
    val style = generatedCoverStyle(title)
    val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    canvas.drawColor(style.background.toArgb())

    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = style.foreground.toArgb()
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
        textSize = heightPx * 0.4f
    }

    val bounds = Rect()
    paint.getTextBounds(style.label, 0, style.label.length, bounds)
    val x = widthPx / 2f
    val y = heightPx / 2f - (bounds.top + bounds.bottom) / 2f
    canvas.drawText(style.label, x, y, paint)

    return bitmap
}

/**
 * 加载书籍封面 Bitmap：有真实封面时解码并缩放，无封面时生成默认封面。
 */
internal suspend fun loadBookCoverBitmap(
    coverPath: String?,
    title: String,
    sizePx: Int = 512,
): Bitmap = withContext(Dispatchers.IO) {
    coverPath?.let { path ->
        runCatching {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, options)
            options.inSampleSize = calculateInSampleSize(options, sizePx, sizePx)
            options.inJustDecodeBounds = false
            BitmapFactory.decodeFile(path, options)
        }.getOrNull()
    } ?: generatedCoverBitmap(title, sizePx, sizePx)
}

private fun calculateInSampleSize(
    options: BitmapFactory.Options,
    reqWidth: Int,
    reqHeight: Int,
): Int {
    val (height, width) = options.outHeight to options.outWidth
    var inSampleSize = 1
    if (height > reqHeight || width > reqWidth) {
        val halfHeight = height / 2
        val halfWidth = width / 2
        while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
            inSampleSize *= 2
        }
    }
    return inSampleSize
}
