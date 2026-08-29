package top.ntutn.sonovelreader.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage
import java.io.File
import top.ntutn.sonovelreader.data.ReaderBlock
import top.ntutn.sonovelreader.data.ReaderPageItem
import top.ntutn.sonovelreader.tts.TtsSentenceLocator

@Composable
internal fun ReaderBlockView(
    blockIndex: Int,
    block: ReaderBlock,
    textStyle: TextStyle,
    palette: ReaderPalette,
    activeSentence: TtsSentenceLocator?,
) {
    when (block) {
        is ReaderBlock.Text -> ReaderTextBlock(blockIndex, block.text, textStyle, palette, activeSentence)
        is ReaderBlock.Image -> ReaderImage(block, palette)
    }
}

@Composable
internal fun ReaderTextBlock(
    blockIndex: Int,
    text: String,
    textStyle: TextStyle,
    palette: ReaderPalette,
    activeSentence: TtsSentenceLocator?,
) {
    val range = activeSentence
        ?.takeIf { it.blockIndex == blockIndex }
        ?.let { it.startOffset.coerceIn(0, text.length) until it.endOffset.coerceIn(0, text.length) }
        ?.takeUnless { it.isEmpty() }
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    var layoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
    LaunchedEffect(range, layoutResult) {
        val activeRange = range ?: return@LaunchedEffect
        val layout = layoutResult ?: return@LaunchedEffect
        val first = layout.getBoundingBox(activeRange.first)
        val last = layout.getBoundingBox(activeRange.last)
        bringIntoViewRequester.bringIntoView(
            Rect(0f, first.top, layout.size.width.toFloat(), last.bottom),
        )
    }
    Text(
        text = highlightedText(text, range, palette),
        style = textStyle,
        color = palette.foreground,
        onTextLayout = { layoutResult = it },
        modifier = Modifier.bringIntoViewRequester(bringIntoViewRequester)
            .semantics { selected = range != null },
    )
}

internal fun activeRangeInSlice(sentence: TtsSentenceLocator?, item: ReaderPageItem.Text): IntRange? {
    if (sentence == null || sentence.blockIndex != item.blockIndex) return null
    val start = maxOf(sentence.startOffset, item.startOffset)
    val end = minOf(sentence.endOffset, item.endOffset)
    return if (start < end) (start - item.startOffset) until (end - item.startOffset) else null
}

internal fun highlightedText(text: String, range: IntRange?, palette: ReaderPalette): AnnotatedString = buildAnnotatedString {
    append(text)
    if (range != null && !range.isEmpty()) {
        addStyle(
            SpanStyle(background = palette.foreground.copy(alpha = 0.18f)),
            range.first.coerceIn(0, text.length),
            (range.last + 1).coerceIn(0, text.length),
        )
    }
}

@Composable
internal fun ReaderImage(image: ReaderBlock.Image, palette: ReaderPalette, height: Dp? = null) {
    val modifier = Modifier.fillMaxWidth().let { base ->
        when {
            height != null -> base.height(height)
            image.aspectRatio != null -> base.heightIn(max = 720.dp).aspectRatio(image.aspectRatio.coerceIn(0.15f, 8f))
            else -> base.heightIn(min = 120.dp, max = 480.dp)
        }
    }
    if (image.absolutePath == null) {
        ImagePlaceholder(image.contentDescription, palette, modifier)
        return
    }
    SubcomposeAsyncImage(
        model = File(image.absolutePath),
        contentDescription = image.contentDescription,
        contentScale = ContentScale.Fit,
        modifier = modifier,
        loading = { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = palette.muted) } },
        error = { ImagePlaceholder(image.contentDescription, palette, Modifier.fillMaxSize()) },
    )
}

@Composable
internal fun ImagePlaceholder(label: String, palette: ReaderPalette, modifier: Modifier = Modifier) {
    Column(
        modifier.background(palette.placeholder).padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Default.BrokenImage, contentDescription = null, tint = palette.muted)
        Text(label, color = palette.muted, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
internal fun ReaderError(message: String, onRetry: () -> Unit, onBack: () -> Unit, palette: ReaderPalette) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("无法打开书籍", style = MaterialTheme.typography.headlineSmall, color = palette.foreground)
        Text(message, Modifier.padding(vertical = 12.dp), color = palette.muted)
        androidx.compose.foundation.layout.Row {
            TextButton(onClick = onBack) { Text("返回书架") }
            TextButton(onClick = onRetry) { Text("重试") }
        }
    }
}
