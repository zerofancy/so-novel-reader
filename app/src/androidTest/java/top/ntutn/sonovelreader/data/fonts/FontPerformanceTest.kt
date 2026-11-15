package top.ntutn.sonovelreader.data.fonts

import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import android.os.Debug
import android.os.SystemClock
import androidx.test.core.app.ApplicationProvider
import java.io.File
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.json.JSONObject

/** Device observations, not universal timing thresholds or peak-memory measurements. */
class FontPerformanceTest {
    @Test fun recordRepositoryLoadAndLargeImportCosts() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Context>()
        val root = File(app.cacheDir, "font-performance-${UUID.randomUUID()}").apply { mkdirs() }
        val context = object : ContextWrapper(app) {
            override fun getNoBackupFilesDir(): File = root
            override fun getApplicationContext(): Context = this
        }
        val report = JSONObject().put("device", android.os.Build.MODEL).put("api", android.os.Build.VERSION.SDK_INT)
        try {
            val repository = FontRepository(context)
            repository.refresh()
            report.put("pssBeforeKiB", Debug.getPss())
            for (font in BuiltinFonts.fonts) {
                var start = SystemClock.elapsedRealtimeNanos()
                assertEquals(font.id, repository.resolve(font.id).id)
                val coldMs = (SystemClock.elapsedRealtimeNanos() - start) / 1_000_000.0
                start = SystemClock.elapsedRealtimeNanos()
                repeat(20) { repository.resolve(font.id) }
                report.put(font.id, JSONObject().put("repositoryColdMs", coldMs)
                    .put("meanCachedMs", (SystemClock.elapsedRealtimeNanos() - start) / 20_000_000.0))
            }
            val largest = BuiltinFonts.fonts.maxBy { it.bytes }
            val source = File(root, "large.ttf")
            source.outputStream().use { out -> app.assets.open(largest.assetPath).use { it.copyTo(out) }; out.write(0) }
            val start = SystemClock.elapsedRealtimeNanos()
            val imported = repository.importFont(Uri.fromFile(source))
            report.put("importBytes", source.length())
                .put("importMs", (SystemClock.elapsedRealtimeNanos() - start) / 1_000_000.0)
                .put("pssAfterKiB", Debug.getPss())
            repository.deleteImported(imported.entry.id)
            File(app.externalCacheDir, "font-performance.json").writeText(report.toString(2))
        } finally { root.deleteRecursively() }
    }
}
