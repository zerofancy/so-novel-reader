package top.ntutn.sonovelreader.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import top.ntutn.sonovelreader.data.*

class BookmarkUiTest {
    @get:Rule val rule = createComposeRule()

    @Test fun readerCreatesJumpsRenamesAndDeletesBookmark() {
        val app = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.app.Application>()
        val container = top.ntutn.sonovelreader.AppContainer(app)
        val epub = java.io.File(app.cacheDir, "bookmarks-${System.nanoTime()}.epub")
        val book = io.documentnode.epub4j.domain.Book().apply {
            metadata.addTitle("书签集成测试")
            for (index in 1..2) addSection("第 $index 章", io.documentnode.epub4j.domain.Resource(
                ("<html xmlns=\"http://www.w3.org/1999/xhtml\"><head><title>第 $index 章</title></head><body>" +
                    (1..100).joinToString("") { "<p>第 $index 章第 $it 段书签正文。</p>" } + "</body></html>").toByteArray(),
                "chapter$index.xhtml"))
        }
        java.io.FileOutputStream(epub).use { io.documentnode.epub4j.epub.EpubWriter().write(book, it) }
        val id = kotlinx.coroutines.runBlocking {
            container.bookRepository.importBooks(listOf(android.net.Uri.fromFile(epub))).successfulBookIds.single()
        }
        val store = androidx.lifecycle.ViewModelStore()
        lateinit var vm: ReaderViewModel
        rule.runOnIdle {
            vm = ReaderViewModel(id, container)
            store.put("reader", vm)
        }
        try {
            rule.waitUntil(10000) { vm.state.value.content != null && !vm.state.value.contentLoading }
            rule.setContent { MaterialTheme { ReaderScreen(vm, {}, {}, {}) } }
            rule.onNodeWithContentDescription("添加书签").performClick()
            rule.onNode(hasSetTextAction()).performTextReplacement("剧情起点")
            rule.onNodeWithText("保存").performClick()
            rule.waitUntil(5000) { vm.state.value.bookmarks.size == 1 }
            rule.runOnIdle { vm.goToChapter(1, 0.5f) }
            rule.waitUntil(5000) { vm.state.value.locator?.chapterIndex == 1 && !vm.state.value.contentLoading }
            rule.onNodeWithContentDescription("目录").performClick()
            rule.onNodeWithText("书签", useUnmergedTree = true).performClick()
            rule.onNodeWithText("剧情起点").performClick()
            rule.waitUntil(5000) { vm.state.value.locator?.chapterIndex == 0 && vm.state.value.bookmarkJumpToken == 1 }
            rule.runOnIdle {
                val before = vm.state.value.locator
                var failure: String? = null
                vm.jumpToBookmark(vm.state.value.bookmarks.single().copy(chapterHref = "missing.xhtml")) { failure = it }
                assertEquals("书签位置已失效", failure)
                assertEquals(before, vm.state.value.locator)
            }
            for (mode in listOf(ReadingMode.PAGED, ReadingMode.FLIP, ReadingMode.SCROLL)) {
                rule.runOnIdle { vm.setMode(mode) }
                rule.waitUntil(5000) { vm.state.value.settings.readingMode == mode }
                val prior = vm.state.value.bookmarkJumpToken
                rule.runOnIdle { vm.jumpToBookmark(vm.state.value.bookmarks.single()) {} }
                rule.waitUntil(5000) { vm.state.value.bookmarkJumpToken == prior + 1 }
                rule.waitForIdle()
                rule.runOnIdle { assertEquals(0, vm.state.value.locator?.chapterIndex) }
            }
            rule.onNodeWithContentDescription("目录").performClick()
            rule.onNodeWithContentDescription("书签更多操作").performClick()
            rule.onNodeWithText("重命名").performClick()
            rule.onNode(hasSetTextAction()).performTextReplacement("新的名称")
            rule.onNodeWithText("保存").performClick()
            rule.waitUntil(5000) { vm.state.value.bookmarks.firstOrNull()?.name == "新的名称" }
            rule.onNodeWithContentDescription("书签更多操作").performClick()
            rule.onNodeWithText("删除").performClick()
            rule.onNodeWithText("删除").performClick()
            rule.waitUntil(5000) { vm.state.value.bookmarks.isEmpty() }
            rule.onNodeWithText("还没有书签").assertExists()
        } finally {
            rule.runOnIdle { store.clear() }
            kotlinx.coroutines.runBlocking { container.bookRepository.deleteBook(id) }
            epub.delete()
        }
    }

    @Test fun nameDialogRejectsBlankAndRetriesFailedSave() {
        var attempts = 0
        var saved = ""
        var dismissed = false
        rule.setContent {
            MaterialTheme {
                BookmarkNameDialog("默认名称", false, { dismissed = true }, {
                    attempts++
                    if (attempts == 1) error("write failed")
                    saved = it
                }, {})
            }
        }
        val field = rule.onNode(hasSetTextAction())
        field.performTextReplacement("  ")
        rule.onNodeWithText("保存").assertIsNotEnabled()
        field.performTextReplacement("  重要情节  ")
        rule.onNodeWithText("保存").performClick()
        rule.onNodeWithText("保存失败，请重试").assertExists()
        rule.onNodeWithText("保存").performClick()
        rule.runOnIdle {
            assertEquals("重要情节", saved)
            assertEquals(2, attempts)
            assertTrue(dismissed)
        }
    }

    @Test fun verticalSameChapterJumpRestoresInsideLongParagraph() {
        val fraction = mutableStateOf(0f)
        val token = mutableIntStateOf(0)
        val content = ReaderContent(listOf(ReaderBlock.Text("书签测试段落。".repeat(1000))))
        var progress = 0f
        rule.setContent {
            MaterialTheme {
                VerticalReader(content = content, settings = ReaderSettings(),
                    palette = ReaderPalette(Color.White, Color.Black, Color.Gray, Color.LightGray),
                    chapterTitle = "书签测试", initialFraction = fraction.value,
                    fragment = null, jumpToken = token.intValue,
                    hasPreviousChapter = false, hasNextChapter = false,
                    onToggleControls = {}, onProgress = { progress = it },
                    onFragmentConsumed = {}, onPreviousChapter = {}, onNextChapter = {})
            }
        }
        rule.runOnIdle { fraction.value = 0.6f; token.intValue++ }
        rule.waitUntil(5000) { progress > 0.55f }
        rule.runOnIdle { fraction.value = 0.2f; token.intValue++ }
        rule.waitUntil(5000) { progress in 0.15f..0.25f }
    }
}
