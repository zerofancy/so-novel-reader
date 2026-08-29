package top.ntutn.sonovelreader.data

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import io.documentnode.epub4j.domain.Book
import io.documentnode.epub4j.domain.TOCReference
import io.documentnode.epub4j.epub.EpubReader
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.ZipFile
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.parser.Parser
import top.ntutn.sonovelreader.data.local.BookEntity
import top.ntutn.sonovelreader.data.local.CategoryEntity
import top.ntutn.sonovelreader.data.local.LibraryDao
import top.ntutn.sonovelreader.data.local.LibraryDao.GroupBookCountRow

class BookRepository(
    private val context: Context,
    private val dao: LibraryDao,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val importMutex = Mutex()

    // ================================================================
    // 书架数据流
    // ================================================================

    fun observeShelf(): Flow<List<ShelfBook>> = dao.observeShelf().map { books ->
        books.map(BookWithProgressMapper)
    }

    /**
     * 顶层书架：所有分组卡片 + 未分组书籍。
     * 用作书架首页的数据来源。
     */
    fun observeTopLevelShelf(): Flow<Pair<List<ShelfGroup>, List<ShelfBook>>> =
        combine(
            dao.observeGroups(),
            dao.observeGroupBookCounts(),
            dao.observeTopLevelBooks(),
        ) { groups, countRows, books ->
            val counts: Map<String, Int> = countRows.associate { it.categoryId to it.bookCount }
            val shelfGroups = groups.map { g ->
                ShelfGroup(id = g.id, name = g.name, bookCount = counts[g.id] ?: 0)
            }
            shelfGroups to books.map(BookWithProgressMapper)
        }

    /** 分组详情：指定分组内的书籍。 */
    fun observeGroupShelf(groupId: String): Flow<List<ShelfBook>> =
        dao.observeBooksInGroup(groupId).map { it.map(BookWithProgressMapper) }

    /**
     * 分组列表 Flow。
     * 供 ViewModel 监听分组是否被删除/重命名，以驱动 UI 回退或标题更新。
     */
    fun observeGroups(): Flow<List<ShelfGroup>> =
        combine(dao.observeGroups(), dao.observeGroupBookCounts()) { groups, countRows ->
            val counts: Map<String, Int> = countRows.associateBy(
                GroupBookCountRow::categoryId,
                GroupBookCountRow::bookCount,
            )
            groups.map { ShelfGroup(it.id, it.name, counts[it.id] ?: 0) }
        }

    // ================================================================
    // 分组 CRUD
    // ================================================================

    suspend fun getGroups(): List<ShelfGroup> = withContext(ioDispatcher) {
        // 仅用于 ViewModel 按 id 找 name / 拿 entity 进行 update/delete；bookCount 不使用
        dao.getGroups().map { ShelfGroup(it.id, it.name, bookCount = 0) }
    }

    suspend fun createGroup(name: String): String = withContext(ioDispatcher) {
        val trimmed = name.trim()
        require(trimmed.isNotEmpty()) { "分组名不能为空" }
        val id = UUID.randomUUID().toString()
        val order = (dao.getGroups().maxOfOrNull { it.sortOrder } ?: -1) + 1
        dao.insertGroup(
            CategoryEntity(
                id = id,
                name = trimmed,
                sortOrder = order,
                createdAt = System.currentTimeMillis(),
            )
        )
        id
    }

    suspend fun renameGroup(id: String, newName: String) = withContext(ioDispatcher) {
        val trimmed = newName.trim()
        require(trimmed.isNotEmpty()) { "分组名不能为空" }
        val existing = dao.getGroups().find { it.id == id } ?: return@withContext
        dao.updateGroup(existing.copy(name = trimmed))
    }

    suspend fun deleteGroup(id: String) = withContext(ioDispatcher) {
        val existing = dao.getGroups().find { it.id == id } ?: return@withContext
        dao.deleteGroup(existing) // FK ON DELETE SET NULL → 组内书籍 categoryId 置空
    }

    // ================================================================
    // 移动书籍
    // ================================================================

    suspend fun moveBookToGroup(bookId: String, targetGroupId: String?) =
        withContext(ioDispatcher) {
            dao.updateBookGroup(bookId, targetGroupId)
        }

    suspend fun importBooks(uris: List<Uri>): ImportBatchResult = withContext(ioDispatcher) {
        importMutex.withLock {
            ImportBatchResult(uris.distinct().map { uri -> importOne(uri) })
        }
    }

    suspend fun deleteBook(bookId: String) = withContext(ioDispatcher) {
        val book = dao.getBook(bookId) ?: return@withContext
        dao.deleteBook(book)
        File(book.epubPath).parentFile?.takeIf { it.name == book.id }?.deleteRecursively()
    }

    suspend fun openBook(bookId: String): ParsedBook = withContext(ioDispatcher) {
        val entity = dao.getBook(bookId) ?: throw IOException("书籍不存在")
        val parsed = FileInputStream(entity.epubPath).use { EpubReader().readEpub(it) }
        val contentRoot = File(entity.contentDirectory)
        val chapters = parsed.spine.spineReferences.mapIndexedNotNull { index, reference ->
            val resource = reference.resource ?: return@mapIndexedNotNull null
            val href = safeRelativePath(resource.href) ?: return@mapIndexedNotNull null
            val file = File(contentRoot, href)
            if (!file.isFile) return@mapIndexedNotNull null
            ReaderChapter(
                title = resource.title?.takeIf(String::isNotBlank)
                    ?: parsed.tableOfContents.tocReferences
                        .firstOrNull { it.resource?.href == resource.href }
                        ?.title
                    ?: "第 ${index + 1} 章",
                href = href,
                absolutePath = file.absolutePath,
            )
        }
        if (chapters.isEmpty()) throw IOException("EPUB 中没有可阅读的正文")
        ParsedBook(entity, chapters, flattenToc(parsed))
    }

    suspend fun readChapter(book: ParsedBook, chapter: ReaderChapter): ReaderContent = withContext(ioDispatcher) {
        val contentRoot = File(book.book.contentDirectory).canonicalFile
        val chapterFile = File(chapter.absolutePath).canonicalFile
        if (!chapterFile.toPath().startsWith(contentRoot.toPath()) || !chapterFile.isFile) {
            throw IOException("章节文件不存在或路径不安全")
        }
        parseReaderContent(chapterFile, contentRoot)
    }

    private suspend fun importOne(uri: Uri): ImportItemResult {
        val name = runCatching { displayName(uri) }
            .getOrElse { uri.lastPathSegment?.substringAfterLast('/') ?: "未知文件.epub" }
        val importDir = File(context.cacheDir, "imports").apply { mkdirs() }
        val tempFile = File.createTempFile("epub-", ".tmp", importDir)
        var finalDirectory: File? = null
        return try {
            val hash = copyAndHash(uri, tempFile)
            dao.getBookByHash(hash)?.let {
                tempFile.delete()
                return ImportItemResult.Existing(name, it.id)
            }
            inspectArchive(tempFile)
            val parsed = FileInputStream(tempFile).use { EpubReader().readEpub(it) }
            if (parsed.spine.isEmpty) throw ImportRejected("EPUB 中没有可阅读的正文")

            val id = UUID.randomUUID().toString()
            val bookDirectory = File(context.filesDir, "books/$id")
            finalDirectory = bookDirectory
            val contentDirectory = File(bookDirectory, "content")
            contentDirectory.mkdirs()
            val epubFile = File(bookDirectory, "book.epub")
            tempFile.copyTo(epubFile, overwrite = false)
            extractResources(parsed, contentDirectory)

            val coverPath = parsed.coverImage?.let { resource ->
                safeRelativePath(resource.href)?.let { File(contentDirectory, it) }
                    ?.takeIf(File::isFile)
                    ?.absolutePath
            }
            val title = parsed.metadata.firstTitle?.trim().takeUnless { it.isNullOrEmpty() }
                ?: name.substringBeforeLast('.').ifBlank { "未命名书籍" }
            val authors = parsed.metadata.authors
                .map { author ->
                    listOf(author.firstname, author.lastname)
                        .filterNotNull()
                        .map(String::trim)
                        .filter(String::isNotEmpty)
                        .joinToString(" ")
                        .ifBlank { author.toString().trim().trimEnd(',') }
                }
                .filter(String::isNotEmpty)
                .joinToString("、")
                .ifBlank { "未知作者" }
            val entity = BookEntity(
                id = id,
                title = title,
                authors = authors,
                sourceFileName = name,
                epubPath = epubFile.absolutePath,
                contentDirectory = contentDirectory.absolutePath,
                coverPath = coverPath,
                contentHash = hash,
                addedAt = System.currentTimeMillis(),
            )
            dao.insertBook(entity)
            ImportItemResult.Imported(name, id)
        } catch (error: Exception) {
            finalDirectory?.deleteRecursively()
            ImportItemResult.Failed(name, error.userMessage())
        } finally {
            tempFile.delete()
        }
    }

    private fun copyAndHash(uri: Uri, destination: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        var copied = 0L
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(destination).use { output ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    copied += count
                    if (copied > MAX_SOURCE_BYTES) throw ImportRejected("文件超过 200 MiB")
                    digest.update(buffer, 0, count)
                    output.write(buffer, 0, count)
                }
            }
        } ?: throw ImportRejected("无法读取所选文件")
        if (copied == 0L) throw ImportRejected("文件内容为空")
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun inspectArchive(file: File) {
        try {
            ZipFile(file).use { zip ->
                val entries = zip.entries().asSequence().toList()
                if (entries.size > MAX_ENTRIES) throw ImportRejected("EPUB 包含过多资源")
                var total = 0L
                entries.forEach { entry ->
                    if (safeRelativePath(entry.name) == null) throw ImportRejected("EPUB 包含不安全路径")
                    if (entry.size > MAX_ENTRY_BYTES) throw ImportRejected("EPUB 中存在过大的单个资源")
                    if (entry.size > 0) {
                        total += entry.size
                        if (total > MAX_EXPANDED_BYTES) throw ImportRejected("EPUB 解压后超过 512 MiB")
                    }
                }
            }
        } catch (error: ImportRejected) {
            throw error
        } catch (_: Exception) {
            throw ImportRejected("文件不是有效的 EPUB")
        }
    }

    private fun extractResources(book: Book, destination: File) {
        book.resources.all.forEach { resource ->
            val href = safeRelativePath(resource.href) ?: throw ImportRejected("EPUB 包含不安全路径")
            val output = File(destination, href)
            ensureInside(destination, output)
            output.parentFile?.mkdirs()
            val data = resource.data
            val safeData = when {
                href.endsWith(".xhtml", true) || href.endsWith(".html", true) || href.endsWith(".htm", true) ->
                    sanitizeEpubMarkup(data, resource.inputEncoding, false)
                href.endsWith(".svg", true) -> sanitizeEpubMarkup(data, resource.inputEncoding, true)
                else -> data
            }
            FileOutputStream(output).use { it.write(safeData) }
        }
    }

    private fun flattenToc(book: Book): List<TocItem> {
        val result = mutableListOf<TocItem>()
        fun append(items: List<TOCReference>, depth: Int) {
            items.forEach { reference ->
                val href = reference.resource?.href?.let(::safeRelativePath)
                if (href != null) {
                    result += TocItem(
                        title = reference.title?.trim().takeUnless { it.isNullOrEmpty() } ?: "未命名章节",
                        href = href,
                        fragment = reference.fragmentId?.takeIf(String::isNotBlank),
                        depth = depth,
                    )
                }
                append(reference.children.orEmpty(), depth + 1)
            }
        }
        append(book.tableOfContents.tocReferences.orEmpty(), 0)
        return result
    }

    private fun displayName(uri: Uri): String {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) return cursor.getString(0) ?: "未知文件.epub"
        }
        return uri.lastPathSegment?.substringAfterLast('/') ?: "未知文件.epub"
    }

    private fun Exception.userMessage(): String = when (this) {
        is ImportRejected -> message ?: "无法导入 EPUB"
        is SecurityException -> "没有读取此文件的权限"
        else -> "无法解析 EPUB：${message ?: "文件可能已损坏或受 DRM 保护"}"
    }

    private object BookWithProgressMapper : (top.ntutn.sonovelreader.data.local.BookWithProgress) -> ShelfBook {
        override fun invoke(value: top.ntutn.sonovelreader.data.local.BookWithProgress) = value.toShelfBook()
    }

    private class ImportRejected(message: String) : IOException(message)

    companion object {
        private const val MAX_SOURCE_BYTES = 200L * 1024 * 1024
        private const val MAX_EXPANDED_BYTES = 512L * 1024 * 1024
        private const val MAX_ENTRY_BYTES = 64L * 1024 * 1024
        private const val MAX_ENTRIES = 10_000

        internal fun safeRelativePath(value: String?): String? {
            if (value.isNullOrBlank()) return null
            val normalized = value.replace('\\', '/').substringBefore('#').trimStart('/')
            if (normalized.isBlank() || value.startsWith('/') || Regex("^[A-Za-z]:").containsMatchIn(value)) return null
            val segments = normalized.split('/')
            if (segments.any { it == ".." || it.isBlank() }) return null
            return segments.filter { it != "." }.joinToString("/")
        }

        private fun ensureInside(root: File, child: File) {
            val rootPath = root.canonicalFile.toPath()
            if (!child.canonicalFile.toPath().startsWith(rootPath)) throw ImportRejected("EPUB 包含不安全路径")
        }
    }
}

internal fun sanitizeEpubMarkup(data: ByteArray, encoding: String?, xml: Boolean): ByteArray {
    val charset = encoding?.takeIf(String::isNotBlank) ?: Charsets.UTF_8.name()
    val parser = if (xml) Parser.xmlParser() else Parser.htmlParser()
    val document = Jsoup.parse(data.inputStream(), charset, "", parser)
    document.select("script, iframe, object, embed").remove()
    document.allElements.forEach { element ->
        element.attributes().asList()
            .filter { it.key.startsWith("on", ignoreCase = true) }
            .forEach { element.removeAttr(it.key) }
    }
    document.select("a[href]").forEach { link ->
        val href = link.attr("href").trim()
        if (href.startsWith("http:", true) || href.startsWith("https:", true) ||
            href.startsWith("javascript:", true) || href.startsWith("file:", true)
        ) {
            link.removeAttr("href")
        }
    }
    if (xml) document.outputSettings().syntax(Document.OutputSettings.Syntax.xml)
    return document.outerHtml().toByteArray(Charsets.UTF_8)
}
