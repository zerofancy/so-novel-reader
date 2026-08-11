package top.ntutn.sonovelreader.data

import android.graphics.BitmapFactory
import java.io.File
import java.net.URLDecoder
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import org.jsoup.parser.Parser

internal fun parseReaderContent(
    chapterFile: File,
    contentRoot: File,
    imageSizeReader: (File) -> Pair<Int, Int>? = ::readRasterImageSize,
): ReaderContent {
    val document = Jsoup.parse(chapterFile, Charsets.UTF_8.name())
    val blocks = mutableListOf<ReaderBlock>()
    val anchorIndexes = linkedMapOf<String, Int>()
    val text = StringBuilder()

    fun flushText() {
        val normalized = text.toString()
            .lines()
            .joinToString("\n") { it.trim() }
            .trim()
        text.clear()
        if (normalized.isNotEmpty()) blocks += ReaderBlock.Text(normalized)
    }

    fun appendText(value: String) {
        val normalized = value.replace('\u00a0', ' ').replace(WHITESPACE, " ")
        if (normalized.isEmpty()) return
        if (text.isNotEmpty() && text.last().isWhitespace() && normalized.first().isWhitespace()) {
            text.append(normalized.drop(1))
        } else {
            text.append(normalized)
        }
    }

    fun recordAnchors(element: Element) {
        listOf(element.id(), element.attr("name"))
            .filter(String::isNotBlank)
            .forEach { anchorIndexes.putIfAbsent(it, blocks.size) }
    }

    fun appendImage(element: Element) {
        flushText()
        val source = element.attr("src").trim()
        val file = resolveImage(contentRoot, chapterFile.parentFile, source)
        val declaredRatio = declaredAspectRatio(element)
        val ratio = declaredRatio ?: file?.let(::svgAspectRatio) ?: file?.let(imageSizeReader)?.let { (width, height) ->
            if (width > 0 && height > 0) width.toFloat() / height else null
        }
        blocks += ReaderBlock.Image(
            absolutePath = file?.absolutePath,
            contentDescription = element.attr("alt").trim().ifBlank { "图片" },
            aspectRatio = ratio?.takeIf { it.isFinite() && it > 0f },
        )
    }

    fun walk(node: Node) {
        when (node) {
            is TextNode -> appendText(node.wholeText)
            is Element -> {
                val tag = node.normalName()
                if (tag in IGNORED_TAGS) return
                recordAnchors(node)
                when {
                    tag == "img" -> appendImage(node)
                    tag == "br" -> {
                        while (text.endsWith(" ")) text.deleteCharAt(text.lastIndex)
                        if (text.isNotEmpty() && text.last() != '\n') text.append('\n')
                    }
                    else -> {
                        val blockElement = tag in BLOCK_TAGS
                        if (blockElement) flushText()
                        node.childNodes().forEach(::walk)
                        if (blockElement) flushText()
                    }
                }
            }
            else -> node.childNodes().forEach(::walk)
        }
    }

    walk(document.body())
    flushText()
    val fallbackBlocks = blocks.ifEmpty { listOf(ReaderBlock.Text("本章没有可显示的内容")) }
    val lastIndex = fallbackBlocks.lastIndex.coerceAtLeast(0)
    val anchors = anchorIndexes.mapValues { (_, index) ->
        ReaderContentPosition(index.coerceIn(0, lastIndex))
    }
    return ReaderContent(fallbackBlocks, anchors)
}

private fun resolveImage(contentRoot: File, chapterParent: File?, source: String): File? {
    if (chapterParent == null || source.isBlank() || source.startsWith('/') || URI_SCHEME.containsMatchIn(source)) return null
    val rawPath = source.substringBefore('#').substringBefore('?').replace('\\', '/')
    val decoded = runCatching {
        URLDecoder.decode(rawPath.replace("+", "%2B"), Charsets.UTF_8.name())
    }.getOrNull() ?: return null
    if (decoded.isBlank() || decoded.startsWith('/')) return null
    val extension = decoded.substringAfterLast('.', "").lowercase()
    if (extension !in SUPPORTED_IMAGE_EXTENSIONS) return null
    val root = runCatching { contentRoot.canonicalFile }.getOrNull() ?: return null
    val candidate = runCatching { File(chapterParent, decoded).canonicalFile }.getOrNull() ?: return null
    if (!candidate.toPath().startsWith(root.toPath()) || !candidate.isFile) return null
    return candidate
}

private fun declaredAspectRatio(element: Element): Float? {
    val width = dimensionNumber(element.attr("width"))
    val height = dimensionNumber(element.attr("height"))
    return if (width != null && height != null && width > 0f && height > 0f) width / height else null
}

private fun svgAspectRatio(file: File): Float? {
    if (!file.extension.equals("svg", true)) return null
    return runCatching {
        val svg = Jsoup.parse(file, Charsets.UTF_8.name(), "", Parser.xmlParser()).selectFirst("svg") ?: return@runCatching null
        declaredAspectRatio(svg) ?: svg.attr("viewBox")
            .trim()
            .split(Regex("[ ,]+"))
            .mapNotNull(String::toFloatOrNull)
            .takeIf { it.size == 4 && it[2] > 0f && it[3] > 0f }
            ?.let { it[2] / it[3] }
    }.getOrNull()
}

private fun dimensionNumber(value: String): Float? =
    Regex("[-+]?[0-9]*\\.?[0-9]+").find(value)?.value?.toFloatOrNull()

private fun readRasterImageSize(file: File): Pair<Int, Int>? {
    if (file.extension.equals("svg", true)) return null
    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.absolutePath, options)
    return if (options.outWidth > 0 && options.outHeight > 0) options.outWidth to options.outHeight else null
}

private val WHITESPACE = Regex("[\\t\\r\\n\\f ]+")
private val URI_SCHEME = Regex("^[A-Za-z][A-Za-z0-9+.-]*:")
private val SUPPORTED_IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp", "gif", "svg")
private val IGNORED_TAGS = setOf("head", "script", "style", "noscript", "iframe", "object", "embed", "table")
private val BLOCK_TAGS = setOf(
    "address", "article", "aside", "blockquote", "div", "footer", "h1", "h2", "h3", "h4", "h5", "h6",
    "header", "hr", "li", "main", "nav", "ol", "p", "pre", "section", "ul",
)
