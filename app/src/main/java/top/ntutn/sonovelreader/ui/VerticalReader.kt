package top.ntutn.sonovelreader.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.distinctUntilChanged
import top.ntutn.sonovelreader.data.ReaderContent
import top.ntutn.sonovelreader.data.ReaderSettings
import top.ntutn.sonovelreader.tts.TtsSentenceLocator

@Composable
internal fun VerticalReader(
    content: ReaderContent,
    settings: ReaderSettings,
    palette: ReaderPalette,
    chapterTitle: String,
    initialFraction: Float,
    fragment: String?,
    jumpToken: Int,
    hasPreviousChapter: Boolean,
    hasNextChapter: Boolean,
    onToggleControls: () -> Unit,
    onProgress: (Float) -> Unit,
    onFragmentConsumed: () -> Unit,
    onPreviousChapter: () -> Unit,
    onNextChapter: () -> Unit,
    activeSentence: TtsSentenceLocator? = null,
    onManualNavigation: () -> Unit = {},
) {
    val listState = rememberLazyListState()
    val textStyle = readerTextStyle(settings, palette.foreground)
    val density = LocalDensity.current
    val thresholdPx = with(density) { CHAPTER_PULL_THRESHOLD.roundToPx().toFloat() }
    val maxDistancePx = with(density) { CHAPTER_PULL_MAX_DISTANCE.roundToPx().toFloat() }
    val pullState = remember(content, hasPreviousChapter, hasNextChapter, thresholdPx, maxDistancePx) {
        ChapterPullState(thresholdPx, maxDistancePx)
    }
    val userDragging = remember { mutableStateOf(false) }
    val nestedScrollConnection = remember(pullState, listState, hasPreviousChapter, hasNextChapter) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (userDragging.value && source == NestedScrollSource.UserInput && available.y != 0f) onManualNavigation()
                val consumed = pullState.consumePreScroll(
                    deltaY = available.y,
                    isUserInput = source == NestedScrollSource.UserInput,
                )
                return Offset(0f, consumed)
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                val consumedY = pullState.consumePostScroll(
                    deltaY = available.y,
                    atStart = !listState.canScrollBackward,
                    atEnd = !listState.canScrollForward,
                    hasPreviousChapter = hasPreviousChapter,
                    hasNextChapter = hasNextChapter,
                    isUserInput = source == NestedScrollSource.UserInput,
                )
                return Offset(0f, consumedY)
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                val hadActivePull = pullState.isActive
                when (pullState.release()) {
                    ChapterPullEdge.PREVIOUS -> onPreviousChapter()
                    ChapterPullEdge.NEXT -> onNextChapter()
                    ChapterPullEdge.NONE -> Unit
                }
                return if (hadActivePull) available else Velocity.Zero
            }
        }
    }
    val pullOffsetPx by animateFloatAsState(
        targetValue = pullState.signedDistancePx,
        animationSpec = if (pullState.isActive) {
            snap()
        } else {
            spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMediumLow,
            )
        },
        label = "chapterPullOffset",
    )

    LaunchedEffect(content, jumpToken) {
        val target = fragment?.let(content.anchors::get) ?: content.positionAt(initialFraction)
        listState.scrollToItem((target.blockIndex + 2).coerceAtLeast(0))
        if (fragment != null) onFragmentConsumed()
    }
    LaunchedEffect(content, listState) {
        snapshotFlow {
            listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index in 2..content.blocks.size + 1 }
        }.distinctUntilChanged().collect { visible ->
            if (visible != null) {
                val blockIndex = visible.index - 2
                val inside = (-visible.offset).toFloat().div(visible.size.coerceAtLeast(1)).coerceIn(0f, 1f)
                onProgress(content.progressAt(blockIndex, inside))
            }
        }
    }

    Box(Modifier.fillMaxSize().clipToBounds()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize()
                .nestedScroll(nestedScrollConnection)
                .graphicsLayer { translationY = pullOffsetPx }
                .pointerInput(onToggleControls) {
                    detectTapGestures { position ->
                        if (position.x in size.width * 0.3f..size.width * 0.7f) onToggleControls()
                    }
                }
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        userDragging.value = true
                        try {
                            do {
                                val event = awaitPointerEvent(PointerEventPass.Final)
                                val change = event.changes.firstOrNull { it.id == down.id }
                            } while (change?.pressed == true)
                        } finally {
                            userDragging.value = false
                        }
                    }
                },
            contentPadding = PaddingValues(start = 22.dp, end = 22.dp, top = 84.dp, bottom = 92.dp),
            verticalArrangement = Arrangement.spacedBy(settings.paragraphSpacingDp.dp),
            overscrollEffect = null,
        ) {
            item(key = "previous-chapter") {
                ChapterNavigation(
                    label = "上一章",
                    enabled = hasPreviousChapter,
                    onClick = onPreviousChapter,
                    palette = palette,
                    edge = ChapterPullEdge.PREVIOUS,
                    pullProgress = if (pullState.edge == ChapterPullEdge.PREVIOUS) pullState.progress else 0f,
                    armed = pullState.edge == ChapterPullEdge.PREVIOUS && pullState.isArmed,
                )
            }
            item(key = "chapter-title") {
                Text(
                    text = chapterTitle,
                    fontSize = (settings.fontSizeSp * 1.3f).sp,
                    fontWeight = FontWeight.Bold,
                    color = palette.foreground,
                )
            }
            itemsIndexed(content.blocks, key = { index, _ -> "block-$index" }) { blockIndex, block ->
                ReaderBlockView(blockIndex, block, textStyle, palette, activeSentence)
            }
            item(key = "next-chapter") {
                ChapterNavigation(
                    label = "下一章",
                    enabled = hasNextChapter,
                    onClick = onNextChapter,
                    palette = palette,
                    edge = ChapterPullEdge.NEXT,
                    pullProgress = if (pullState.edge == ChapterPullEdge.NEXT) pullState.progress else 0f,
                    armed = pullState.edge == ChapterPullEdge.NEXT && pullState.isArmed,
                )
            }
        }
    }
}
