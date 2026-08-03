package top.ntutn.sonovelreader.ui

import java.io.File
import org.jsoup.Jsoup
import top.ntutn.sonovelreader.data.ReaderSettings
import top.ntutn.sonovelreader.data.ReaderTheme
import top.ntutn.sonovelreader.data.ReadingMode

internal fun buildReaderHtml(file: File, settings: ReaderSettings, darkSystem: Boolean): String {
    val document = Jsoup.parse(file, Charsets.UTF_8.name())
    document.select("#so-reader-style, .so-reader-navigation").remove()
    if (document.selectFirst("meta[name=viewport]") == null) {
        document.head().appendElement("meta")
            .attr("name", "viewport")
            .attr("content", "width=device-width, initial-scale=1, maximum-scale=1")
    }
    val colors = when (settings.theme) {
        ReaderTheme.LIGHT -> ReaderColors("#FAF8F3", "#25231F", "#6D685E")
        ReaderTheme.DARK -> ReaderColors("#171717", "#E7E2D8", "#AAA49A")
        ReaderTheme.SEPIA -> ReaderColors("#F2E8CF", "#43392A", "#786A55")
        ReaderTheme.SYSTEM -> if (darkSystem) {
            ReaderColors("#171717", "#E7E2D8", "#AAA49A")
        } else {
            ReaderColors("#FAF8F3", "#25231F", "#6D685E")
        }
    }
    val layout = if (settings.readingMode == ReadingMode.PAGED) {
        """
        html { min-height: 100%; overflow-x: hidden !important; scroll-behavior: smooth; }
        body { box-sizing: border-box; margin: 0 !important; padding: 84px 22px 92px !important; overflow-wrap: anywhere; }
        """.trimIndent()
    } else {
        """
        html { min-height: 100%; overflow-x: hidden; }
        body { box-sizing: border-box; margin: 0 !important; padding: 28px 22px 56px !important; overflow-wrap: anywhere; }
        .so-reader-navigation { display: block; padding: 22px 8px; text-align: center; color: ${colors.muted}; text-decoration: none; }
        """.trimIndent()
    }
    document.head().appendElement("style")
        .attr("id", "so-reader-style")
        .appendText(
            """
            :root { color-scheme: ${if (colors.background == "#171717") "dark" else "light"}; background: ${colors.background}; }
            html, body { background: ${colors.background} !important; color: ${colors.foreground} !important; }
            body, body * { font-size: ${settings.fontSizeSp}px; line-height: ${settings.lineHeight} !important; }
            h1, h2, h3, h4, h5, h6 { line-height: 1.35 !important; break-after: avoid; }
            img, svg, video { max-width: 100% !important; height: auto !important; break-inside: avoid; }
            pre, table { max-width: 100%; overflow-x: auto; }
            a { color: inherit; }
            $layout
            """.trimIndent(),
        )
    if (settings.readingMode == ReadingMode.SCROLL) {
        document.body().prepend("<a class=\"so-reader-navigation\" href=\"sonovel://previous\">上一章</a>")
        document.body().append("<a class=\"so-reader-navigation\" href=\"sonovel://next\">下一章</a>")
    }
    return document.outerHtml()
}

private data class ReaderColors(val background: String, val foreground: String, val muted: String)

internal fun restorePositionScript(mode: ReadingMode, fraction: Float, fragment: String?): String {
    val safeFraction = fraction.coerceIn(0f, 1f)
    val escapedFragment = fragment?.replace("\\", "\\\\")?.replace("'", "\\'")
    val target = if (escapedFragment != null) {
        "document.getElementById('$escapedFragment')?.scrollIntoView();"
    } else if (mode == ReadingMode.PAGED) {
        "const root=document.scrollingElement||document.documentElement; const max=Math.max(root.scrollHeight-window.innerHeight,0); const step=Math.max(window.innerHeight-176,1); window.scrollTo(0,Math.min(max,Math.round(max*$safeFraction/step)*step));"
    } else {
        "const max=Math.max(document.documentElement.scrollHeight-window.innerHeight,0); window.scrollTo(0,max*$safeFraction);"
    }
    return "requestAnimationFrame(()=>requestAnimationFrame(()=>{$target}));"
}
