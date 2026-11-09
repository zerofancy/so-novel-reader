// Adapted from PTQFlipper 1.1.0 (MIT, Copyright 2023 MPGA).
// See assets/licenses/PTQFlipper.txt.
package ptq.mpga.ptqbookpageview.widget

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput

@Composable
internal fun PTQBookPageViewInner(
    bounds: Rect,
    controller: PTQBookPageBitmapController,
    callbacks: PTQBookPageViewScopeImpl,
    pageTurnController: PTQPageTurnController?,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
) {
    val config = LocalPTQBookPageViewConfig.current
    val engine = remember(bounds, controller, config) { CurlEngine(bounds, controller, config) }
    SideEffect {
        engine.callbacks = callbacks
        engine.onNext = onNext
        engine.onPrevious = onPrevious
    }
    DisposableEffect(engine, pageTurnController) {
        val detach = pageTurnController?.attach(engine)
        onDispose {
            engine.cancel()
            detach?.invoke()
        }
    }
    val ratio by animateFloatAsState(
        targetValue = if (engine.state == State.EnterAnimStart || engine.state == State.ExitAnimStart) 1f else 0f,
        finishedListener = { engine.animationFinished() },
        animationSpec = TweenSpec(easing = LinearEasing, durationMillis = when (engine.state) {
            State.EnterAnimStart -> engine.animDuration[0]
            State.ExitAnimStart -> engine.animDuration[1]
            else -> 0
        }),
    )
    Box(Modifier.fillMaxSize().pointerInput(engine) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false)
            engine.pointerDown = true
            try {
                do {
                    val event = awaitPointerEvent(PointerEventPass.Final)
                } while (event.changes.any { it.pressed })
            } finally {
                engine.pointerDown = false
            }
        }
    }.then(engine.inputModifier())) {
        // The first idle composition can run before SideEffect installs the engine callbacks.
        engine.Draw(ratio, callbacks)
    }
}
