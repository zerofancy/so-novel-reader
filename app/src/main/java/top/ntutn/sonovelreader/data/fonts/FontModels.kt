package top.ntutn.sonovelreader.data.fonts

import androidx.compose.ui.text.font.FontFamily
import kotlinx.serialization.Serializable

const val SYSTEM_FONT_ID = "system"
const val MAX_FONT_BYTES = 50L * 1024 * 1024

enum class FontSource { SYSTEM, BUILTIN, IMPORTED }

@Serializable
data class ImportedFont(
    val id: String,
    val displayName: String,
    val originalName: String,
    val fileName: String,
    val sha256: String,
    val bytes: Long,
    val importedAt: Long,
    val format: String,
)

data class FontEntry(
    val id: String,
    val displayName: String,
    val source: FontSource,
    val sha256: String = "",
    val originalName: String = "",
    val importedAt: Long = 0,
)

data class FontCatalogState(
    val entries: List<FontEntry> = BuiltinFonts.entries,
    val loading: Boolean = true,
    val error: FontError? = null,
)

data class ResolvedReaderFont(val id: String, val cacheKey: String, val family: FontFamily)
data class FontImportResult(val entry: FontEntry, val duplicate: Boolean, val builtin: Boolean)
data class FontDeletionResult(val cleanupPending: Boolean)

enum class FontError(val userMessage: String) {
    UNSUPPORTED("不支持此字体格式，请选择静态 TTF 或 OTF"),
    CORRUPT("字体文件为空或已损坏"),
    TOO_LARGE("字体文件超过 50 MiB"),
    SOURCE_UNREADABLE("无法读取字体来源，请重新选择文件"),
    NO_SPACE("存储空间不足"),
    LOAD_FAILED("字体加载失败，请重试或选择其他字体"),
    CATALOG_WRITE("字体目录写入失败，请重试"),
    CATALOG_READ("字体列表读取失败，请重试"),
    NOT_FOUND("所选字体不可用，已使用系统默认"),
}

class FontException(val code: FontError, cause: Throwable? = null) :
    Exception(code.userMessage, cause) {
    /** Only permanent resource failures justify repairing the persisted selected ID. */
    val isPermanentSelectionFailure: Boolean
        get() = when (code) {
            FontError.NOT_FOUND, FontError.CORRUPT, FontError.UNSUPPORTED, FontError.TOO_LARGE -> true
            FontError.LOAD_FAILED -> {
                val seen = java.util.Collections.newSetFromMap(java.util.IdentityHashMap<Throwable, Boolean>())
                var error = cause
                var retryable = false
                while (error != null && seen.add(error)) {
                    if ((error is java.io.IOException && error !is java.io.EOFException) ||
                        error is SecurityException || error is java.util.concurrent.CancellationException
                    ) retryable = true
                    error = error.cause
                }
                !retryable
            }
            else -> false
        }
}

internal fun ImportedFont.entry() = FontEntry(
    id, displayName, FontSource.IMPORTED, sha256, originalName, importedAt,
)
