package top.ntutn.sonovelreader.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import top.ntutn.sonovelreader.MainActivity
import top.ntutn.sonovelreader.data.ReadingMode
import top.ntutn.sonovelreader.tts.TtsPlaybackStatus

internal class ReaderPageTurner {
    var ready: () -> Boolean = { false }
    var turn: (Boolean) -> Unit = {}
    var cancel: () -> Unit = {}
}

internal val LocalReaderPageTurner = staticCompositionLocalOf<ReaderPageTurner?> { null }

internal fun volumePagingAllowed(mode: ReadingMode, status: TtsPlaybackStatus): Boolean =
    mode != ReadingMode.SCROLL && status !in setOf(
        TtsPlaybackStatus.PREPARING, TtsPlaybackStatus.PLAYING, TtsPlaybackStatus.PAUSED,
    )

@Composable
internal fun ReaderVolumeKeys(
    turner: ReaderPageTurner, enabled: Boolean, viewModel: ReaderViewModel, renderedState: ReaderUiState,
) {
    val activity = LocalContext.current as? MainActivity ?: return
    val owner = LocalLifecycleOwner.current
    val currentEnabled = rememberUpdatedState(enabled)
    val displayedState = rememberUpdatedState(renderedState)
    DisposableEffect(activity, owner, turner, viewModel) {
        val handler: (Boolean) -> Boolean = { next ->
            val playback = activity.playbackManager.state.value
            val state = viewModel.state.value
            val displayed = displayedState.value
            if (currentEnabled.value && owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) &&
                activity.hasWindowFocus() && !activity.playbackManager.startPending &&
                !state.loading && !state.contentLoading && state.error == null && state.locator != null &&
                state.content != null && state.parsedBook != null && state.content === displayed.content &&
                state.locator.chapterHref == displayed.locator?.chapterHref &&
                state.settings == displayed.settings &&
                volumePagingAllowed(state.settings.readingMode, playback.status) && turner.ready()
            ) {
                turner.turn(next)
                true
            } else false
        }
        val cancel = { turner.cancel() }
        activity.volumePageHandler = handler
        activity.cancelVolumeNavigation = cancel
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) turner.cancel()
        }
        owner.lifecycle.addObserver(observer)
        onDispose {
            owner.lifecycle.removeObserver(observer)
            turner.cancel()
            if (activity.volumePageHandler === handler) {
                activity.volumePageHandler = null
                activity.cancelVolumeNavigation = null
            }
        }
    }
}
