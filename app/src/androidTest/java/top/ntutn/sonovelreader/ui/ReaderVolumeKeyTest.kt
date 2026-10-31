package top.ntutn.sonovelreader.ui

import android.app.Application
import android.media.AudioManager
import android.net.Uri
import android.os.SystemClock
import android.view.KeyEvent
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.captureToImage
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import io.documentnode.epub4j.domain.Book
import io.documentnode.epub4j.domain.Resource
import io.documentnode.epub4j.epub.EpubWriter
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import top.ntutn.sonovelreader.MainActivity
import top.ntutn.sonovelreader.SoNovelReaderApplication
import top.ntutn.sonovelreader.data.ReadingMode
import top.ntutn.sonovelreader.data.local.BookmarkEntity
import top.ntutn.sonovelreader.tts.TtsPlaybackState
import top.ntutn.sonovelreader.tts.TtsPlaybackStatus

class ReaderVolumeKeyTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    private fun key(next: Boolean) {
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(
            if (next) KeyEvent.KEYCODE_VOLUME_DOWN else KeyEvent.KEYCODE_VOLUME_UP)
        rule.waitForIdle()
    }

    @Test fun volumePagingAndFallbackOnConnectedDevice() {
        val app = ApplicationProvider.getApplicationContext<Application>() as SoNovelReaderApplication
        val container = app.container
        val settings = runBlocking { container.settingsRepository.settings.first() }
        val playback = container.ttsPlaybackManager.state.value
        check(playback.status !in listOf(TtsPlaybackStatus.PLAYING, TtsPlaybackStatus.PREPARING, TtsPlaybackStatus.PAUSED))
        val audio = app.getSystemService(AudioManager::class.java)
        val volume = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
        val originalStream = rule.activity.volumeControlStream
        fun resetVolume(): Int {
            val target = minOf(2, audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC))
            audio.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0)
            rule.waitUntil(5000) { audio.getStreamVolume(AudioManager.STREAM_MUSIC) == target }
            return target
        }
        rule.runOnIdle { rule.activity.volumeControlStream = AudioManager.STREAM_MUSIC }
        val epub = File(app.cacheDir, "volume-keys-${System.nanoTime()}.epub")
        val book = Book().apply {
            metadata.addTitle("音量键翻页验证")
            for (chapter in 1..3) addSection("第 $chapter 章", Resource(
                ("<html xmlns=\"http://www.w3.org/1999/xhtml\"><body>" +
                    (1..20).joinToString("") { "<p>第 $chapter 章第 $it 段。音量键翻页验证正文，检查翻页方向、跨章及阅读进度保持正确。</p>" } +
                    "</body></html>").toByteArray(), "chapter$chapter.xhtml"))
        }
        epub.outputStream().use { EpubWriter().write(book, it) }
        val id = runBlocking { container.bookRepository.importBooks(listOf(Uri.fromFile(epub))).successfulBookIds.single() }
        val store = ViewModelStore()
        lateinit var vm: ReaderViewModel
        fun ready() = rule.waitUntil(10000) { vm.state.value.content != null && !vm.state.value.contentLoading && !vm.state.value.loading }
        fun jump(chapter: Int, fraction: Float) {
            val token = vm.state.value.bookmarkJumpToken
            rule.runOnIdle {
                val target = vm.state.value.parsedBook!!.chapters[chapter]
                vm.jumpToBookmark(BookmarkEntity("test-jump", id, "test", target.href, chapter,
                    target.title, fraction, 0L, 0L)) { assertNull(it) }
            }
            ready()
            rule.waitUntil(5000) { vm.state.value.bookmarkJumpToken > token }
            rule.waitForIdle()
        }
        try {
            rule.runOnIdle {
                vm = ReaderViewModel(id, container)
                store.put("reader", vm)
            }
            ready()
            rule.runOnIdle { rule.activity.setContent { MaterialTheme { ReaderScreen(vm, {}, {}, {}) } } }
            rule.waitForIdle()
            for (mode in listOf(ReadingMode.PAGED, ReadingMode.FLIP)) {
                rule.runOnIdle { vm.setMode(mode) }
                rule.waitUntil(5000) { vm.state.value.settings.readingMode == mode }
                jump(0, 0f)
                // Control bars shown: delegate to Android, with no page change.
                val start = vm.state.value.locator
                val controlVolume = resetVolume()
                key(true)
                rule.waitUntil(5000) { audio.getStreamVolume(AudioManager.STREAM_MUSIC) < controlVolume }
                assertEquals(start, vm.state.value.locator)
                rule.onNodeWithTag("reader-layout").performTouchInput { click(center) }
                rule.waitForIdle()
                val beforeVolume = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
                key(true)
                rule.waitUntil(5000) { vm.state.value.locator!!.chapterFraction > 0f }
                assertEquals(beforeVolume, audio.getStreamVolume(AudioManager.STREAM_MUSIC))
                key(false)
                rule.waitUntil(5000) { vm.state.value.locator!!.chapterFraction == 0f }

                // Hold through animation completion; repeat DOWNs must still do nothing.
                val downTime = SystemClock.uptimeMillis()
                rule.runOnIdle {
                    assertTrue(rule.activity.dispatchKeyEvent(KeyEvent(downTime, downTime, KeyEvent.ACTION_DOWN,
                        KeyEvent.KEYCODE_VOLUME_DOWN, 0)))
                }
                rule.waitForIdle()
                val once = vm.state.value.locator
                rule.runOnIdle {
                    repeat(8) { repeat ->
                        assertTrue(rule.activity.dispatchKeyEvent(KeyEvent(downTime, SystemClock.uptimeMillis(),
                            KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_VOLUME_DOWN, repeat + 1)))
                    }
                    assertTrue(rule.activity.dispatchKeyEvent(KeyEvent(downTime, SystemClock.uptimeMillis(),
                        KeyEvent.ACTION_UP, KeyEvent.KEYCODE_VOLUME_DOWN, 0)))
                }
                rule.waitForIdle()
                assertEquals(once, vm.state.value.locator)

                // TTS reservation is global, including a different book and paused playback.
                for (status in listOf(TtsPlaybackStatus.PREPARING, TtsPlaybackStatus.PLAYING, TtsPlaybackStatus.PAUSED)) {
                    rule.runOnIdle { container.ttsPlaybackManager.publish(TtsPlaybackState(status, "another-book")) }
                    rule.waitForIdle()
                    val before = vm.state.value.locator
                    val ttsVolume = resetVolume()
                    key(true)
                    rule.waitUntil(5000) { audio.getStreamVolume(AudioManager.STREAM_MUSIC) < ttsVolume }
                    assertEquals("$mode / $status", before, vm.state.value.locator)
                }
                rule.runOnIdle { container.ttsPlaybackManager.publish(TtsPlaybackState()) }
                rule.waitForIdle()
                jump(0, 1f)
                key(true)
                rule.waitUntil(5000) { vm.state.value.locator?.chapterIndex == 1 && !vm.state.value.contentLoading }
                rule.waitForIdle()
                assertEquals(0f, vm.state.value.locator!!.chapterFraction)
                key(false)
                rule.waitUntil(5000) { vm.state.value.locator?.chapterIndex == 0 && !vm.state.value.contentLoading }
                rule.waitForIdle()
                assertTrue(vm.state.value.locator!!.chapterFraction > 0f)
                jump(0, 0f)
                key(false)
                assertEquals(0f, vm.state.value.locator!!.chapterFraction)
                jump(2, 1f)
                val end = vm.state.value.locator
                key(true)
                assertEquals(end, vm.state.value.locator)
                File(app.externalCacheDir, "volume-keys-$mode.png").outputStream().use {
                    rule.onNodeWithTag("reader-layout").captureToImage().asAndroidBitmap()
                        .compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
                }
                // Open the panel from visible controls; volume keys must not navigate.
                rule.onNodeWithTag("reader-layout").performTouchInput { click(center) }
                rule.onNodeWithContentDescription("目录").performClick()
                val beforePanel = vm.state.value.locator
                key(true)
                assertEquals(beforePanel, vm.state.value.locator)
                InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
                rule.waitForIdle()
            }
            rule.runOnIdle { vm.setMode(ReadingMode.SCROLL) }
            rule.waitUntil(5000) { vm.state.value.settings.readingMode == ReadingMode.SCROLL }
            jump(1, 0.5f)
            rule.onNodeWithTag("reader-layout").performTouchInput { click(center) }
            rule.waitForIdle()
            val scroll = vm.state.value.locator
            val scrollVolume = resetVolume()
            key(true)
            rule.waitUntil(5000) { audio.getStreamVolume(AudioManager.STREAM_MUSIC) < scrollVolume }
            key(false)
            assertEquals(scroll, vm.state.value.locator)
        } finally {
            rule.runOnIdle {
                rule.activity.setContent {}
                store.clear()
                container.ttsPlaybackManager.publish(playback)
                rule.activity.volumeControlStream = originalStream
            }
            runBlocking {
                container.settingsRepository.setReadingMode(settings.readingMode)
                container.bookRepository.deleteBook(id)
            }
            audio.setStreamVolume(AudioManager.STREAM_MUSIC, volume, 0)
            epub.delete()
        }
    }
}
