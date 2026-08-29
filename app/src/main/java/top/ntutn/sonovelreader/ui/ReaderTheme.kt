package top.ntutn.sonovelreader.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.ntutn.sonovelreader.data.ReaderSettings
import top.ntutn.sonovelreader.data.ReaderTheme
import top.ntutn.sonovelreader.data.TocItem

internal data class ReaderPalette(
    val background: Color,
    val foreground: Color,
    val muted: Color,
    val placeholder: Color,
)

@Composable
internal fun readerPalette(theme: ReaderTheme): ReaderPalette {
    val darkSystem = androidx.compose.foundation.isSystemInDarkTheme()
    return when (theme) {
        ReaderTheme.LIGHT -> ReaderPalette(Color(0xFFFAF8F3), Color(0xFF25231F), Color(0xFF6D685E), Color(0xFFE8E3D9))
        ReaderTheme.DARK -> ReaderPalette(Color(0xFF171717), Color(0xFFE7E2D8), Color(0xFFAAA49A), Color(0xFF292826))
        ReaderTheme.SEPIA -> ReaderPalette(Color(0xFFF2E8CF), Color(0xFF43392A), Color(0xFF786A55), Color(0xFFE3D5B5))
        ReaderTheme.SYSTEM -> if (darkSystem) {
            ReaderPalette(Color(0xFF171717), Color(0xFFE7E2D8), Color(0xFFAAA49A), Color(0xFF292826))
        } else {
            ReaderPalette(Color(0xFFFAF8F3), Color(0xFF25231F), Color(0xFF6D685E), Color(0xFFE8E3D9))
        }
    }
}

@Composable
internal fun readerTextStyle(settings: ReaderSettings, color: Color, applyIndent: Boolean = true): TextStyle = TextStyle(
    color = color,
    fontSize = settings.fontSizeSp.sp,
    lineHeight = (settings.fontSizeSp * settings.lineHeight).sp,
    textIndent = if (applyIndent && settings.firstLineIndent) TextIndent(firstLine = (settings.fontSizeSp * 2).sp) else null,
)

@Composable
internal fun TocRow(item: TocItem, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick)
            .background(if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface)
            .padding(start = (20 + item.depth * 18).dp, end = 20.dp, top = 14.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(12.dp))
        Text(item.title, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
    }
}

@Composable
internal fun ChapterNavigation(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    palette: ReaderPalette,
    edge: ChapterPullEdge,
    pullProgress: Float,
    armed: Boolean,
) {
    val progress = pullProgress.coerceIn(0f, 1f)
    val scale by animateFloatAsState(
        targetValue = if (armed) 1.06f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "chapterNavigationScale",
    )
    val state = when {
        !enabled -> "没有$label"
        armed -> "松手切换$label"
        progress > 0f && edge == ChapterPullEdge.PREVIOUS -> "继续下拉切换$label"
        progress > 0f -> "继续上拉切换$label"
        else -> "点击切换$label"
    }
    val textColor = when {
        !enabled -> palette.placeholder
        else -> lerp(palette.muted, palette.foreground, progress)
    }
    Box(
        Modifier.fillMaxWidth()
            .testTag("chapter-navigation-${edge.name.lowercase()}")
            .semantics { stateDescription = state }
            .background(
                color = palette.foreground.copy(alpha = 0.16f * progress),
                shape = RoundedCornerShape(20.dp),
            ),
        contentAlignment = Alignment.Center,
    ) {
        TextButton(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier.graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
        ) {
            Text(label, color = textColor, fontWeight = if (armed) FontWeight.SemiBold else FontWeight.Normal)
        }
    }
}

internal val CHAPTER_PULL_THRESHOLD = 64.dp
internal val CHAPTER_PULL_MAX_DISTANCE = 112.dp
