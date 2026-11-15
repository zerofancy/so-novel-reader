package top.ntutn.sonovelreader.ui

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.onRoot
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import top.ntutn.sonovelreader.AppContainer
import top.ntutn.sonovelreader.data.ReadingMode
import top.ntutn.sonovelreader.data.fonts.BuiltinFonts

class FontFeatureTest {
    @get:Rule val rule = createComposeRule()
    private val app get() = ApplicationProvider.getApplicationContext<Application>()

    @Test fun chooseImportDeleteAndOfflineAbout() {
        val container = AppContainer(app)
        val store = ViewModelStore()
        lateinit var vm: FontSettingsViewModel
        val about = mutableStateOf(false)
        val messages = java.util.concurrent.CopyOnWriteArrayList<String>()
        rule.runOnIdle { vm = FontSettingsViewModel(container); store.put("fonts", vm) }
        try {
            rule.waitUntil(15_000) { vm.appearance.value != null && !vm.catalog.value.loading }
            rule.setContent { MaterialTheme {
                if (about.value) AboutScreen({ about.value = false }) else FontSettingsScreen(vm, {}, { messages.add(it) })
            } }
            for (font in BuiltinFonts.fonts) {
                rule.onNodeWithTag("font-${font.id}").performScrollTo().performClick()
                rule.waitUntil(15_000) { vm.appearance.value?.font?.id == font.id }
                rule.onNodeWithTag("font-${font.id}").assertIsSelected()
            }
            val source = File(app.cacheDir, "imported-font-${System.nanoTime()}.ttf")
            source.outputStream().use { output ->
                app.assets.open(BuiltinFonts.fonts.first().assetPath).use { it.copyTo(output) }
                output.write(0) // Valid unused trailing data; different hash from builtin for import coverage.
            }
            rule.runOnIdle { vm.importFont(Uri.fromFile(source)) }
            rule.waitUntil(15_000) { !vm.importing.value }
            assertTrue("Import events: $messages", vm.catalog.value.entries.any { it.source.name == "IMPORTED" })
            val imported = vm.catalog.value.entries.first { it.source.name == "IMPORTED" }
            source.delete()
            rule.onNodeWithTag("font-${imported.id}").performScrollTo().performClick()
            rule.waitUntil(15_000) { vm.appearance.value?.font?.id == imported.id }
            runBlocking { assertEquals(imported.id, container.settingsRepository.settings.first().fontId) }
            // Fresh resolver proves source URI/file is no longer required.
            runBlocking {
                val fresh = top.ntutn.sonovelreader.data.fonts.FontRepository(app)
                fresh.refresh()
                assertEquals(imported.id, fresh.resolve(imported.id).id)
            }
            rule.onNodeWithContentDescription("管理字体 ${imported.displayName}").performClick()
            rule.onNodeWithText("删除").performClick()
            rule.onNodeWithText("取消").performClick()
            rule.onNodeWithTag("font-${imported.id}").assertIsSelected()
            rule.onNodeWithContentDescription("管理字体 ${imported.displayName}").performClick()
            rule.onNodeWithText("删除").performClick()
            rule.onNodeWithText("删除").performClick()
            rule.waitUntil(15_000) { vm.catalog.value.entries.none { it.id == imported.id } && vm.appearance.value?.font?.id == "system" }
            rule.runOnIdle { about.value = true }
            rule.waitUntil(15_000) { rule.onNodeWithTag("credit-builtin:zhuque").fetchSemanticsNodeOrNull() != null }
            rule.onNodeWithTag("about-screen").performScrollToNode(hasTestTag("credit-builtin:zhuque"))
            rule.onNode(hasText("展开 LICENSE / NOTICE（离线）") and
                hasAnyAncestor(hasTestTag("credit-builtin:zhuque"))).performScrollTo().performClick()
            rule.waitUntil(10_000) { rule.onNodeWithText("复制完整授权文本").fetchSemanticsNodeOrNull() != null }
            screenshot("fonts-about-license")
        } finally {
            runBlocking { container.settingsRepository.setFontId("system") }
            rule.runOnIdle { store.clear(); container.close() }
        }
    }

    @Test fun allBuiltinsReflowAllModesAndPreserveChapterPosition() {
        val container = AppContainer(app)
        val epub = File(app.cacheDir, "font-reader-${System.nanoTime()}.epub")
        val book = io.documentnode.epub4j.domain.Book().apply {
            metadata.addTitle("字体重排设备测试")
            addSection("字体章节", io.documentnode.epub4j.domain.Resource(
                ("<html xmlns=\"http://www.w3.org/1999/xhtml\"><head><title>字体章节</title></head><body>" +
                    (1..100).joinToString("") { "<p>第${it}段：山色入帘青，灯下读书声。Aa012345，。！？繁體漢字。</p>" } +
                    "</body></html>").toByteArray(), "chapter.xhtml"))
        }
        epub.outputStream().use { io.documentnode.epub4j.epub.EpubWriter().write(book, it) }
        val id = runBlocking { container.bookRepository.importBooks(listOf(Uri.fromFile(epub))).successfulBookIds.single() }
        val store = ViewModelStore()
        lateinit var vm: ReaderViewModel
        rule.runOnIdle { vm = ReaderViewModel(id, container); store.put("reader", vm) }
        try {
            rule.waitUntil(15_000) { vm.state.value.content != null && vm.state.value.font != null && !vm.state.value.contentLoading }
            rule.setContent { MaterialTheme { ReaderScreen(vm, {}, {}, {}) } }
            for (mode in ReadingMode.entries) {
                runBlocking { container.settingsRepository.setReadingMode(mode) }
                rule.waitUntil(10_000) { vm.state.value.settings.readingMode == mode }
                rule.runOnIdle { vm.goToChapter(0, 0.5f) }
                rule.waitUntil(10_000) { !vm.state.value.contentLoading && vm.state.value.locator!!.chapterFraction > .4f }
                for (font in BuiltinFonts.fonts) {
                    runBlocking { container.readerFontManager.select(font.id) }
                    rule.waitUntil(15_000) { vm.state.value.font?.id == font.id }
                    rule.waitForIdle()
                    val state = vm.state.value
                    assertEquals(0, state.locator!!.chapterIndex)
                    assertTrue("$mode ${font.id}: ${state.locator.chapterFraction}", state.locator.chapterFraction in .35f.. .65f)
                    // Obsolete layout callbacks must not overwrite the new locator.
                    rule.runOnIdle { vm.updateReaderFraction(0f, state.locator.chapterHref, state.bookmarkJumpToken, state.layoutGeneration - 1) }
                    assertTrue(vm.state.value.locator!!.chapterFraction > .3f)
                    screenshot("font-${mode.name}-${font.id.substringAfter(':')}")
                }
            }
        } finally {
            rule.runOnIdle { store.clear() }
            runBlocking {
                container.settingsRepository.setFontId("system")
                container.settingsRepository.setReadingMode(ReadingMode.SCROLL)
                container.bookRepository.deleteBook(id)
            }
            epub.delete()
            container.close()
        }
    }

    private fun screenshot(name: String) {
        File(app.externalCacheDir, "$name.png").outputStream().use {
            rule.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}

private fun androidx.compose.ui.test.SemanticsNodeInteraction.fetchSemanticsNodeOrNull() =
    try { fetchSemanticsNode() } catch (_: AssertionError) { null }
