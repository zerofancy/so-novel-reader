package top.ntutn.sonovelreader.data.fonts

import android.content.Context
import android.content.ContextWrapper
import androidx.test.core.app.ApplicationProvider
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import top.ntutn.sonovelreader.data.ReaderFontManager
import top.ntutn.sonovelreader.data.SettingsRepository

class ReaderFontManagerTest {
    private val app = ApplicationProvider.getApplicationContext<Context>()
    private val settings = SettingsRepository(app)

    @Test fun missingSelectionRepairsSettingsAndRetainsStartupNotice() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        try {
            settings.setFontId("imported:${UUID.randomUUID()}")
            val manager = ReaderFontManager(FontRepository(app), settings, scope)
            val result = withTimeout(10_000) { manager.appearance.first { it != null } }!!
            assertEquals("system", result.font.id)
            withTimeout(10_000) { settings.settings.first { it.fontId == "system" } }
            // Subscribe AFTER fallback has completed: the notice must not disappear.
            assertTrue(withTimeout(5_000) { manager.messages.first() }.contains("系统默认"))
        } finally { scope.cancel(); settings.setFontId("system") }
    }

    @Test fun temporaryCatalogFailureDoesNotClearSelectionAndCanRetry() = runBlocking {
        val root = File(app.cacheDir, "font-manager-${UUID.randomUUID()}").apply { mkdirs() }
        val wrapper = object : ContextWrapper(app) {
            override fun getNoBackupFilesDir(): File = root
            override fun getApplicationContext(): Context = this
        }
        val catalog = File(root, "fonts/catalog.json").apply { parentFile!!.mkdirs(); writeText("broken") }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        try {
            val id = "imported:${UUID.randomUUID()}"
            settings.setFontId(id)
            val manager = ReaderFontManager(FontRepository(wrapper), settings, scope)
            withTimeout(10_000) { manager.appearance.first { it != null } }
            assertEquals(id, settings.settings.first().fontId)
            assertEquals("system", manager.appearance.value!!.font.id)
            catalog.writeText("{\"version\":1,\"fonts\":[]}")
            manager.refresh()
            withTimeout(10_000) { settings.settings.first { it.fontId == "system" } }
            assertEquals("system", manager.appearance.value!!.font.id)
        } finally { scope.cancel(); settings.setFontId("system"); root.deleteRecursively() }
    }
}
