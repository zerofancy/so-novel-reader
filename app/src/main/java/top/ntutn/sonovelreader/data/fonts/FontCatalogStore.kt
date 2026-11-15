package top.ntutn.sonovelreader.data.fonts

import android.util.AtomicFile
import java.io.File
import java.util.UUID
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
internal data class FontCatalogDocument(val version: Int = 1, val fonts: List<ImportedFont> = emptyList())

/** Only accepts internal UUID paths; never trusts paths supplied by the document provider. */
internal class FontCatalogStore(val directory: File) {
    private val json = Json { ignoreUnknownKeys = true }
    private val atomic = AtomicFile(File(directory, "catalog.json"))

    fun read(): List<ImportedFont> {
        try {
            check(directory.isDirectory || directory.mkdirs())
            // AtomicFile.openRead restores a pending .bak before returning.
            if (!atomic.baseFile.exists() && !File(directory, "catalog.json.bak").exists()) return emptyList()
            val bytes = atomic.openRead().use { input ->
                // A corrupt/untrusted catalog must not allocate arbitrary amounts of memory.
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    require(output.size() + count <= 16 * 1024 * 1024)
                    output.write(buffer, 0, count)
                }
                output.toByteArray()
            }
            val document = json.decodeFromString<FontCatalogDocument>(bytes.toString(Charsets.UTF_8))
            require(document.version == 1)
            require(document.fonts.map { it.id }.distinct().size == document.fonts.size)
            require(document.fonts.map { it.fileName }.distinct().size == document.fonts.size)
            document.fonts.forEach { font ->
                path(font)
                require(font.bytes in 1..MAX_FONT_BYTES)
                require(font.sha256.matches(Regex("[a-f0-9]{64}")))
                require(font.displayName == FontValidation.displayName(font.displayName, null))
            }
            return document.fonts
        } catch (e: Exception) {
            // Preserve the original bytes and formal font files for user retry/diagnosis.
            throw FontException(FontError.CATALOG_READ, e)
        }
    }

    fun write(fonts: List<ImportedFont>) {
        var output: java.io.FileOutputStream? = null
        try {
            directory.mkdirs()
            val payload = json.encodeToString(FontCatalogDocument(fonts = fonts)).toByteArray()
            require(payload.size <= 16 * 1024 * 1024) { "Catalog exceeds safe decode capacity" }
            output = atomic.startWrite()
            output.write(payload)
            atomic.finishWrite(output)
            // Some Android AtomicFile versions only log rename failures. Do not publish a
            // successful catalog operation until the committed base contains these bytes.
            check(atomic.baseFile.isFile && atomic.baseFile.readBytes().contentEquals(payload))
        } catch (e: Exception) {
            output?.let { atomic.failWrite(it) }
            throw FontException(FontError.CATALOG_WRITE, e)
        }
    }

    fun path(font: ImportedFont): File {
        val uuid = font.id.removePrefix("imported:")
        require(font.id == "imported:${UUID.fromString(uuid)}")
        require(font.format in listOf("ttf", "otf"))
        require(font.fileName == "$uuid.${font.format}")
        val result = File(directory, font.fileName)
        require(result.canonicalFile.parentFile == directory.canonicalFile)
        return result
    }

    /** Called only after a successfully decoded catalog, never on catalog failure. */
    fun recover(fonts: List<ImportedFont>) {
        val referenced = fonts.map { it.fileName }.toSet()
        directory.listFiles()?.forEach { file ->
            val controlled = file.name.matches(Regex("[a-f0-9-]{36}\\.(ttf|otf)"))
            // Cleanup is best effort; transient failures must not turn a valid catalog
            // into an empty/error list, and the same orphan will be retried next start.
            runCatching {
                if (file.canonicalFile.parentFile == directory.canonicalFile &&
                    (file.name.startsWith("pending-") || (controlled && file.name !in referenced))
                ) file.delete()
            }
        }
    }
}
