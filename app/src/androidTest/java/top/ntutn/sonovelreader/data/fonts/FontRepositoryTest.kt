package top.ntutn.sonovelreader.data.fonts

import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import java.io.File
import java.util.UUID
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

/** Real API 29+ platform parser + AtomicFile coverage; requires an Android device/emulator. */
class FontRepositoryTest {
    private val app = ApplicationProvider.getApplicationContext<Context>()
    private val root = File(app.cacheDir, "fonts-test-${UUID.randomUUID()}").apply { mkdirs() }
    private val context = object : ContextWrapper(app) {
        override fun getNoBackupFilesDir(): File = root
        override fun getApplicationContext(): Context = this
    }

    @After fun clean() { root.deleteRecursively() }

    @Test fun builtinFontsLoadOfflineAndMatchManifest() = runBlocking {
        val repository = FontRepository(context)
        repository.refresh()
        assertNull(repository.catalog.value.error)
        assertEquals(4, repository.catalog.value.entries.size)
        for (font in BuiltinFonts.fonts) {
            val file = File(root, "verification.ttf")
            val copy = app.assets.open(font.assetPath).use { input ->
                file.outputStream().use { FontValidation.copy(input, it) }
            }
            assertEquals(font.sha256, copy.sha256)
            assertEquals(font.bytes, copy.bytes)
            FontValidation.inspect(file)
            assertEquals(font.id, repository.resolve(font.id).id)
            assertTrue(repository.readLicense(font.licenseAssetPath).contains("SIL OPEN FONT LICENSE"))
        }
    }

    @Test fun importingBuiltinIsDeduplicatedWithoutAddingCatalogEntry() = runBlocking {
        val repository = FontRepository(context)
        repository.refresh()
        val font = BuiltinFonts.fonts.first()
        val file = File(root, "source.ttf")
        app.assets.open(font.assetPath).use { input -> file.outputStream().use { input.copyTo(it) } }
        val result = repository.importFont(Uri.fromFile(file))
        assertTrue(result.duplicate)
        assertTrue(result.builtin)
        assertEquals(font.id, result.entry.id)
        assertEquals(4, repository.catalog.value.entries.size)
        assertFalse(File(root, "fonts").listFiles()!!.any { it.name.startsWith("pending-") })
    }

    @Test fun importedBytesSurviveSourceDeletionAndRepositoryRestart() = runBlocking {
        val font = BuiltinFonts.fonts.first()
        val source = File(root, "source.bin")
        app.assets.open(font.assetPath).use { input -> source.outputStream().use { input.copyTo(it) } }
        // An ignored SFNT trailing byte creates a different hash without changing outlines.
        source.appendBytes(byteArrayOf(0))
        val first = FontRepository(context)
        first.refresh()
        val imported = first.importFont(Uri.fromFile(source))
        assertFalse(imported.duplicate)
        assertEquals(FontSource.IMPORTED, imported.entry.source)
        assertEquals(5, first.catalog.value.entries.size)
        assertEquals(imported.entry.id, first.importFont(Uri.fromFile(source)).entry.id)
        assertTrue(source.delete())
        val restarted = FontRepository(context)
        restarted.refresh()
        assertEquals(imported.entry.id, restarted.resolve(imported.entry.id).id)
        assertFalse(restarted.deleteImported(imported.entry.id).cleanupPending)
        assertEquals(4, restarted.catalog.value.entries.size)
        try {
            restarted.resolve(imported.entry.id)
            fail("Deleted font resolved")
        } catch (e: FontException) { assertEquals(FontError.NOT_FOUND, e.code) }
    }

    @Test fun corruptCatalogIsAnErrorAndDoesNotDeleteFormalFiles() = runBlocking {
        val directory = File(root, "fonts").apply { mkdirs() }
        val orphan = File(directory, "${UUID.randomUUID()}.ttf").apply { writeBytes(byteArrayOf(1)) }
        val catalog = File(directory, "catalog.json").apply { writeText("broken") }
        val repository = FontRepository(context)
        repository.refresh()
        assertEquals(FontError.CATALOG_READ, repository.catalog.value.error)
        assertTrue(orphan.exists())
        assertEquals("broken", catalog.readText())
        assertEquals("system", repository.resolve("system").id)
        catalog.writeText("{\"version\":1,\"fonts\":[]}")
        repository.refresh()
        assertNull(repository.catalog.value.error)
        assertFalse(orphan.exists())
    }

    @Test fun failedCatalogCommitRollsBackNewFormalFile() = runBlocking {
        val repository = FontRepository(context)
        repository.refresh()
        val directory = File(root, "fonts")
        // A nonempty directory where AtomicFile expects its base prevents final commit.
        File(directory, "catalog.json").apply { mkdirs() }
            .resolve("blocker").writeText("block")
        val source = File(root, "source.ttf")
        app.assets.open(BuiltinFonts.fonts.first().assetPath).use { input ->
            source.outputStream().use { input.copyTo(it) }
        }
        source.appendBytes(byteArrayOf(0))
        try {
            repository.importFont(Uri.fromFile(source))
            fail("Invalid catalog destination accepted")
        } catch (e: FontException) { assertEquals(FontError.CATALOG_WRITE, e.code) }
        assertEquals(4, repository.catalog.value.entries.size)
        assertFalse(directory.listFiles()!!.any { it.extension in listOf("ttf", "otf") })
    }

    @Test fun providerFailureCleansTemporaryBytesWithoutPublishingEntry() = runBlocking {
        val repository = FontRepository(context)
        repository.refresh()
        val source = object : java.io.InputStream() {
            private var reads = 0
            override fun read() = throw AssertionError("Bulk read expected")
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                if (reads++ > 0) throw java.io.IOException("provider unavailable")
                b[off] = 0
                return 1
            }
        }
        try {
            repository.importFontSource({ "source.ttf" }, { source })
            fail("Provider error swallowed")
        } catch (e: FontException) {
            assertEquals(FontError.SOURCE_UNREADABLE, e.code)
            assertFalse(e.isPermanentSelectionFailure)
        }
        assertEquals(4, repository.catalog.value.entries.size)
        assertTrue(File(root, "fonts").listFiles()!!.isEmpty())
    }

    @Test fun cancellationDuringCopyClosesSourceAndCleansPendingFile() = runBlocking {
        val repository = FontRepository(context)
        repository.refresh()
        val started = java.util.concurrent.CountDownLatch(1)
        val proceed = java.util.concurrent.CountDownLatch(1)
        var closed = false
        val source = object : java.io.InputStream() {
            override fun read() = throw AssertionError("Bulk read expected")
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                started.countDown()
                check(proceed.await(5, java.util.concurrent.TimeUnit.SECONDS))
                b[off] = 0
                return 1
            }
            override fun close() { closed = true }
        }
        val operation = launch { repository.importFontSource({ "source.ttf" }, { source }) }
        try {
            withContext(Dispatchers.IO) {
                check(started.await(5, java.util.concurrent.TimeUnit.SECONDS))
                operation.cancel()
                proceed.countDown()
            }
            operation.join()
            assertTrue(operation.isCancelled)
            assertTrue(closed)
            assertEquals(4, repository.catalog.value.entries.size)
            assertTrue(File(root, "fonts").listFiles()!!.isEmpty())
        } finally {
            proceed.countDown()
            operation.cancel()
        }
    }

    @Test fun storeRejectsTraversalAndRecoversInterruptedAtomicWrite() {
        val store = FontCatalogStore(File(root, "fonts").apply { mkdirs() })
        val id = UUID.randomUUID().toString()
        val record = ImportedFont("imported:$id", "Example", "x.ttf", "$id.ttf", "a".repeat(64), 1, 1, "ttf")
        store.write(listOf(record))
        assertEquals(listOf(record), store.read())
        val base = File(store.directory, "catalog.json")
        base.copyTo(File(store.directory, "catalog.json.bak"))
        base.writeText("interrupted")
        assertEquals(listOf(record), store.read())
        try {
            store.path(record.copy(fileName = "../outside.ttf"))
            fail("Traversal accepted")
        } catch (_: IllegalArgumentException) { }
    }
}
