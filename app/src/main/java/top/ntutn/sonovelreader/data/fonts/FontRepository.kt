package top.ntutn.sonovelreader.data.fonts

import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import android.provider.OpenableColumns
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import androidx.compose.ui.text.font.FontFamily
import java.io.File
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** IO-bound private catalog. Selection persistence deliberately belongs to the settings coordinator. */
class FontRepository(context: Context) {
    private val app = context.applicationContext
    private val store = FontCatalogStore(File(app.noBackupFilesDir, "fonts"))
    private val mutex = Mutex()
    private var imported: List<ImportedFont> = emptyList()
    private var initialized = false
    private val mutableCatalog = MutableStateFlow(FontCatalogState())
    val catalog: StateFlow<FontCatalogState> = mutableCatalog.asStateFlow()
    private val cache = object : LinkedHashMap<String, ResolvedReaderFont>(4, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ResolvedReaderFont>?) = size > 3
    }

    suspend fun refresh() = withContext(Dispatchers.IO) {
        mutex.withLock {
            try {
                imported = store.read()
                initialized = true
                store.recover(imported)
                cache.clear()
                publish()
            } catch (e: FontException) {
                initialized = false
                mutableCatalog.value = FontCatalogState(BuiltinFonts.entries, false, e.code)
            }
        }
    }

    suspend fun resolve(id: String): ResolvedReaderFont = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (id == SYSTEM_FONT_ID) return@withLock ResolvedReaderFont(id, "system", FontFamily.Default)
            val builtin = BuiltinFonts.fonts.firstOrNull { it.id == id }
            val custom = if (builtin == null) {
                requireCatalog()
                imported.firstOrNull { it.id == id } ?: throw FontException(FontError.NOT_FOUND)
            } else null
            val hash = builtin?.sha256 ?: custom!!.sha256
            val key = "$id:$hash"
            cache[key]?.let { return@withLock it }
            val typeface = try {
                if (builtin != null) Typeface.Builder(app.assets, builtin.assetPath).build()
                else {
                    val file = store.path(custom!!)
                    requireRegularFile(file)
                    // Verify immutable bytes on cache miss, including restored/tampered private files.
                    val digest = file.inputStream().use {
                        FontValidation.copy(CancellableInput(it, currentCoroutineContext()), DiscardOutput)
                    }
                    if (digest.sha256 != hash || digest.bytes != custom.bytes) throw FontException(FontError.CORRUPT)
                    FontValidation.inspect(file)
                    Typeface.Builder(file).build()
                }
            } catch (e: CancellationException) { throw e
            } catch (e: FontException) { throw e
            } catch (e: Exception) { throw FontException(FontError.LOAD_FAILED, e) }
            currentCoroutineContext().ensureActive()
            if (typeface == null) throw FontException(FontError.LOAD_FAILED)
            validatePlatform(typeface)
            ResolvedReaderFont(id, key, FontFamily(typeface)).also { cache[key] = it }
        }
    }

    suspend fun importFont(uri: Uri): FontImportResult = importFontSource(
        { originalName(uri) }, { app.contentResolver.openInputStream(uri) },
    )

    /** Stream seam for deterministic provider-fault/cancellation tests; same production transaction. */
    internal suspend fun importFontSource(
        sourceName: () -> String?,
        openSource: () -> java.io.InputStream?,
    ): FontImportResult = withContext(Dispatchers.IO) {
        mutex.withLock {
            requireCatalog()
            val temp = File(store.directory, "pending-${UUID.randomUUID()}")
            var formal: File? = null
            var committed = false
            try {
                val original = sourceName()
                val copied = try {
                    val input = openSource()
                        ?: throw FontException(FontError.SOURCE_UNREADABLE)
                    input.use { source ->
                        temp.outputStream().use { target ->
                            FontValidation.copy(CancellableInput(source, currentCoroutineContext()), target)
                                .also { target.fd.sync() }
                        }
                    }
                } catch (e: SecurityException) { throw FontException(FontError.SOURCE_UNREADABLE, e) }
                BuiltinFonts.fonts.firstOrNull { it.sha256 == copied.sha256 }?.let {
                    return@withLock FontImportResult(it.entry(), true, true)
                }
                imported.firstOrNull { it.sha256 == copied.sha256 }?.let {
                    return@withLock FontImportResult(it.entry(), true, false)
                }
                val inspection = FontValidation.inspect(temp)
                val face = try {
                    Typeface.Builder(temp).build() ?: throw FontException(FontError.LOAD_FAILED)
                } catch (e: FontException) { throw e
                } catch (e: Exception) {
                    throw FontException(FontError.LOAD_FAILED, e)
                }
                validatePlatform(face)
                currentCoroutineContext().ensureActive()
                val uuid = UUID.randomUUID().toString()
                val record = ImportedFont(
                    "imported:$uuid", FontValidation.displayName(inspection.name, original),
                    FontValidation.displayName(original, null), "$uuid.${inspection.format}",
                    copied.sha256, copied.bytes, System.currentTimeMillis(), inspection.format,
                )
                formal = store.path(record)
                if (!temp.renameTo(formal)) throw FontException(FontError.CATALOG_WRITE)
                val updated = imported + record
                store.write(updated)
                committed = true
                imported = updated
                publish()
                FontImportResult(record.entry(), false, false)
            } catch (e: CancellationException) { throw e
            } catch (e: FontException) { throw e
            } catch (e: IOException) {
                val noSpace = generateSequence<Throwable>(e) { it.cause }
                    .any { it.message?.contains("ENOSPC") == true || it.message?.contains("No space") == true }
                throw FontException(if (noSpace) FontError.NO_SPACE else FontError.SOURCE_UNREADABLE, e)
            } catch (e: Exception) { throw FontException(FontError.CORRUPT, e)
            } finally {
                // Cleanup must never mask a provider failure or CancellationException.
                removeControlledFile(temp)
                // A failed deletion becomes a controlled orphan retried after successful catalog read.
                if (!committed) formal?.let { removeControlledFile(it) }
            }
        }
    }

    /** Caller must first persist system when removing its selected font; never accepts external paths. */
    suspend fun deleteImported(id: String): FontDeletionResult = withContext(Dispatchers.IO) {
        mutex.withLock {
            requireCatalog()
            val record = imported.firstOrNull { it.id == id } ?: throw FontException(FontError.NOT_FOUND)
            val file = store.path(record)
            val updated = imported.filterNot { it.id == id }
            store.write(updated)
            imported = updated
            cache.keys.removeAll { it.startsWith("$id:") }
            publish()
            FontDeletionResult(removeControlledFile(file))
        }
    }

    suspend fun readLicense(assetPath: String): String = withContext(Dispatchers.IO) {
        require(BuiltinFonts.fonts.any { it.licenseAssetPath == assetPath })
        app.assets.open(assetPath).bufferedReader().use { it.readText() }
    }

    private fun requireCatalog() {
        if (!initialized) throw FontException(FontError.CATALOG_READ)
    }

    private fun publish() {
        val entries = imported.sortedWith(compareByDescending<ImportedFont> { it.importedAt }.thenBy { it.id })
        mutableCatalog.value = FontCatalogState(BuiltinFonts.entries + entries.map { it.entry() }, false)
    }

    private fun originalName(uri: Uri): String? = try {
        app.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        }
    } catch (_: Exception) { null }

    /** Returns whether cleanup remains pending; never misreports EIO/EACCES as absent. */
    private fun removeControlledFile(file: File): Boolean = try {
        Os.remove(file.path)
        false
    } catch (e: ErrnoException) {
        e.errno != OsConstants.ENOENT
    } catch (_: SecurityException) {
        true
    }

    private fun requireRegularFile(file: File) {
        try {
            if (!OsConstants.S_ISREG(Os.stat(file.path).st_mode)) throw FontException(FontError.CORRUPT)
        } catch (e: ErrnoException) {
            if (e.errno == OsConstants.ENOENT || e.errno == OsConstants.ENOTDIR) {
                throw FontException(FontError.NOT_FOUND, e)
            }
            // File.exists/isFile hide EACCES/EIO as false, incorrectly causing permanent fallback.
            throw FontException(FontError.LOAD_FAILED, IOException("Private font storage unavailable", e))
        }
    }

    private fun validatePlatform(typeface: Typeface) {
        try {
            val paint = Paint().apply { this.typeface = typeface; textSize = 20f }
            val width = paint.measureText("山色入帘青 Aa0123456789，。！？")
            if (!width.isFinite() || width <= 0) throw FontException(FontError.LOAD_FAILED)
        } catch (e: FontException) { throw e
        } catch (e: Exception) { throw FontException(FontError.LOAD_FAILED, e) }
    }

    private object DiscardOutput : java.io.OutputStream() {
        override fun write(b: Int) = Unit
        override fun write(b: ByteArray, off: Int, len: Int) = Unit
    }

    private class CancellableInput(
        input: java.io.InputStream,
        private val coroutineContext: kotlin.coroutines.CoroutineContext,
    ) : java.io.FilterInputStream(input) {
        override fun read(): Int {
            coroutineContext.ensureActive()
            return super.read()
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            coroutineContext.ensureActive()
            return super.read(b, off, len)
        }
    }
}
