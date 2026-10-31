package top.ntutn.sonovelreader.ui

import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

internal class PagedNavigation(private val pager: PagerState, private val scope: CoroutineScope) {
    var pointerDown = false
    private var job: Job? = null
    var enabled = false
    var onManual: () -> Unit = {}
    var onPastStart: () -> Unit = {}
    var onPastEnd: () -> Unit = {}

    fun turn(next: Boolean, fromKey: Boolean = true) {
        if (!enabled || (fromKey && pointerDown) || job?.isActive == true || pager.isScrollInProgress) return
        job = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            onManual()
            val target = pager.settledPage + if (next) 1 else -1
            when {
                target < 0 -> onPastStart()
                target >= pager.pageCount -> onPastEnd()
                else -> pager.animateScrollToPage(target)
            }
        }
    }

    fun cancel() { job?.cancel() }
}

@Composable
internal fun rememberPagedNavigation(
    pager: PagerState,
    ready: Boolean,
    onManual: () -> Unit,
    onPastStart: () -> Unit,
    onPastEnd: () -> Unit,
): PagedNavigation {
    val scope = rememberCoroutineScope()
    val navigation = remember(pager) { PagedNavigation(pager, scope) }
    navigation.enabled = ready
    navigation.onManual = onManual
    navigation.onPastStart = onPastStart
    navigation.onPastEnd = onPastEnd
    val turner = LocalReaderPageTurner.current
    val currentReady = rememberUpdatedState(ready)
    DisposableEffect(pager, turner) {
        turner?.ready = { currentReady.value }
        turner?.turn = { navigation.turn(it) }
        turner?.cancel = navigation::cancel
        onDispose {
            navigation.cancel()
            turner?.ready = { false }
            turner?.turn = {}
            turner?.cancel = {}
        }
    }
    return navigation
}
