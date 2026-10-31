package ptq.mpga.ptqbookpageview.widget

/** Programmatic navigation for [PTQBookPageView], independent of the input source. */
class PTQPageTurnController {
    private var engine: CurlEngine? = null

    /** True after layout and page bitmaps are ready. */
    val isReady: Boolean get() = engine?.ready == true

    /** Includes pointer gestures and animations; requests while busy are dropped. */
    val isBusy: Boolean get() = engine?.busy == true

    /** Animate one page: true for next, false for previous. Unready or busy views do nothing. */
    fun turnPage(next: Boolean) { engine?.turn(next) }

    /** Cancel navigation without committing the target page. */
    fun cancel() { engine?.cancel() }

    internal fun attach(viewEngine: CurlEngine): () -> Unit {
        engine = viewEngine
        return {
            if (engine === viewEngine) engine = null
        }
    }
}
