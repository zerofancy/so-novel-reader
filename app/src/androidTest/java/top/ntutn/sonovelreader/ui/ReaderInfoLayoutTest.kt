package top.ntutn.sonovelreader.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import top.ntutn.sonovelreader.data.ReaderBlock
import top.ntutn.sonovelreader.data.ReaderContent
import top.ntutn.sonovelreader.data.ReaderSettings
import top.ntutn.sonovelreader.data.ReaderTheme

class ReaderInfoLayoutTest {
    @get:Rule val compose = createComposeRule()
    private val palette = readerPalette(ReaderTheme.LIGHT, false)

    @Test
    fun togglingInfoDoesNotChangeViewportOrPagination() {
        val visible = mutableStateOf(true)
        val info = mutableStateOf<ReaderDisplayInfo?>(null)
        val onInfo: (ReaderDisplayInfo) -> Unit = { info.value = it }
        val content = ReaderContent(List(80) { ReaderBlock.Text("这是用于分页回归测试的正文段落。".repeat(8)) }, emptyMap())
        compose.setContent {
            MaterialTheme {
                ReaderInfoLayout(visible.value, "测试章节", false, info.value, palette) {
                    Box(Modifier.fillMaxSize().testTag("reader-body")) {
                        PagedReader(content, ReaderSettings(), palette, "测试章节", 0f, null, 0,
                            false, false, { visible.value = !visible.value }, {}, {}, {}, {}, onDisplayInfo = onInfo)
                    }
                }
            }
        }
        compose.waitUntil { info.value?.pageCount != null }
        val initial = info.value!!
        assertTrue(initial.pageCount!! > 1)
        compose.onNodeWithTag("reader-body").performTouchInput { click(Offset(width * 0.9f, height * 0.5f)) }
        compose.waitUntil { info.value?.page == 2 }
        val secondPage = info.value
        compose.onNodeWithTag("reader-body").performTouchInput { click(center) }
        compose.onNodeWithTag("reader-info-chapter").assertDoesNotExist()
        compose.runOnIdle { assertEquals(secondPage, info.value) }
        compose.onNodeWithTag("reader-body").performTouchInput { click(center) }
        compose.onAllNodesWithTag("reader-info-chapter").filterToOne(SemanticsMatcher("visible page") { it.boundsInRoot.width > 0f && it.boundsInRoot.height > 0f }).assertExists()
        compose.runOnIdle { assertEquals(secondPage, info.value) }
    }

    @Test
    fun infoRowsStayOutsideBodyAndVisibilityKeepsBounds() {
        val visible = mutableStateOf(true)
        compose.setContent {
            MaterialTheme {
                ReaderInfoLayout(visible.value, "章节标题".repeat(30), false, ReaderDisplayInfo(0, 3, 18), palette, pageAttached = false) {
                    Box(Modifier.fillMaxSize().padding(LocalReaderPadding.current).testTag("viewport"))
                }
            }
        }
        val body = compose.onNodeWithTag("viewport").fetchSemanticsNode().boundsInRoot
        val title = compose.onNodeWithTag("reader-info-chapter").fetchSemanticsNode().boundsInRoot
        val progress = compose.onNodeWithTag("reader-info-progress").fetchSemanticsNode().boundsInRoot
        assertTrue(title.bottom <= body.top)
        assertTrue(progress.top >= body.bottom)
        compose.runOnIdle { visible.value = false }
        assertEquals(body, compose.onNodeWithTag("viewport").fetchSemanticsNode().boundsInRoot)
    }

    @Test
    fun draggingAcrossChapterShowsEachPagesOwnHeadingAndNumber() {
        val content = ReaderContent(listOf(ReaderBlock.Text("当前章正文")))
        val next = ReaderChapterContent("下一章", ReaderContent(listOf(ReaderBlock.Text("下一章正文"))))
        compose.setContent {
            MaterialTheme {
                ReaderInfoLayout(true, "当前章", false, null, palette) {
                    Box(Modifier.fillMaxSize().testTag("moving-pages")) {
                        PagedReader(content, ReaderSettings(), palette, "当前章", 0f, null, 0,
                            false, true, {}, {}, {}, {}, {}, nextChapter = next)
                    }
                }
            }
        }
        compose.onNodeWithContentDescription("当前章").assertExists()
        compose.onNodeWithTag("moving-pages").performTouchInput {
            down(Offset(width * 0.85f, height * 0.5f))
            moveBy(Offset(-width * 0.45f, 0f), delayMillis = 300)
        }
        // Both page headers must be present during the gesture, with independent page metadata.
        compose.onNodeWithContentDescription("当前章").assertExists()
        compose.onNodeWithContentDescription("下一章").assertExists()
        compose.onAllNodesWithContentDescription("本章第 1 页，共 1 页").assertCountEquals(2)
        val nextHeading = compose.onNodeWithContentDescription("下一章").fetchSemanticsNode().boundsInRoot
        assertTrue(nextHeading.left > 0f)
        compose.onNodeWithTag("moving-pages").performTouchInput { cancel() }
    }

    @Test
    fun flipReaderReportsSettledPage() {
        val info = mutableStateOf<ReaderDisplayInfo?>(null)
        val onInfo: (ReaderDisplayInfo) -> Unit = { info.value = it }
        val content = ReaderContent(List(30) { ReaderBlock.Text("仿真翻页测试段落。".repeat(12)) })
        compose.setContent {
            MaterialTheme {
                ReaderInfoLayout(true, "仿真翻页", false, info.value, palette) {
                    Box(Modifier.fillMaxSize().testTag("flip-body")) {
                        FlipReader(content, ReaderSettings(), palette, "仿真翻页", 0f, null, 0,
                            false, false, {}, {}, {}, {}, {}, onDisplayInfo = onInfo)
                    }
                }
            }
        }
        compose.waitUntil { info.value?.page == 1 }
        compose.onNodeWithTag("flip-body").performTouchInput { click(Offset(width * 0.9f, height * 0.5f)) }
        compose.waitUntil(5_000) { info.value?.page == 2 }
        compose.onNodeWithContentDescription("本章第 2 页，共 ${info.value!!.pageCount} 页").assertExists()
    }

    @Test
    fun shortScrollChapterShowsCompleteWithoutChangingSavedPosition() {
        val info = mutableStateOf<ReaderDisplayInfo?>(null)
        val onInfo: (ReaderDisplayInfo) -> Unit = { info.value = it }
        compose.setContent {
            MaterialTheme {
                ReaderInfoLayout(true, "短章", true, info.value, palette) {
                    VerticalReader(ReaderContent(listOf(ReaderBlock.Text("正文")), emptyMap()), ReaderSettings(),
                        palette, "短章", 0f, null, 0, false, false, {}, {}, {}, {}, {}, onDisplayInfo = onInfo)
                }
            }
        }
        compose.waitUntil { info.value?.percent == 100 }
        compose.onNodeWithContentDescription("本章 100%").assertExists()
    }
}
