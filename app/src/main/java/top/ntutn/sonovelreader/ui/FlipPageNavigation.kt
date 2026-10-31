package top.ntutn.sonovelreader.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import ptq.mpga.ptqbookpageview.widget.PTQPageTurnController

/** Connects the reader's input policy to the reusable flipper's navigation API. */
@Composable
internal fun rememberFlipPageNavigation(): PTQPageTurnController {
    val controller = remember { PTQPageTurnController() }
    val turner = LocalReaderPageTurner.current
    DisposableEffect(controller, turner) {
        turner?.ready = { controller.isReady }
        turner?.turn = controller::turnPage
        turner?.cancel = controller::cancel
        onDispose {
            controller.cancel()
            turner?.ready = { false }
            turner?.turn = {}
            turner?.cancel = {}
        }
    }
    return controller
}
