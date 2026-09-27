package top.ntutn.sonovelreader.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import top.ntutn.sonovelreader.data.ReaderBlock
import top.ntutn.sonovelreader.data.ReaderContent
import top.ntutn.sonovelreader.data.ReaderSettings
import top.ntutn.sonovelreader.data.ReaderTheme
import top.ntutn.sonovelreader.data.ReadingMode
import top.ntutn.sonovelreader.tts.TtsSentenceLocator
import top.ntutn.sonovelreader.tts.TtsVoiceCatalogState

class ReaderScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val palette = ReaderPalette(
        background = Color.White,
        foreground = Color.Black,
        muted = Color.Gray,
        placeholder = Color.LightGray,
    )

    @Test
    fun themeCardsSelectEachPresetAndClearPreviousSelection() {
        val settings = mutableStateOf(ReaderSettings())
        composeRule.setContent {
            MaterialTheme {
                SettingsScreen(
                    settings = settings.value,
                    showAiOperationEntry = false,
                    aiAllowOperation = false,
                    onOpenAiOperationSettings = {},
                    onModeChange = {},
                    onFontSizeChange = {},
                    onLineHeightChange = {},
                    onFirstLineIndentChange = {},
                    onParagraphSpacingChange = {},
                    onThemeChange = { settings.value = settings.value.copy(theme = it) },
                    onKeepScreenOnChange = {},
                    ttsVoices = TtsVoiceCatalogState(),
                    onTtsRateChange = {},
                    onTtsPitchChange = {},
                    onTtsVoiceChange = {},
                )
            }
        }

        composeRule.onNodeWithTag("reader-theme-SYSTEM").assertIsSelected()
        val labels = listOf("跟随系统", "浅色", "深色", "米黄纸", "亚麻纸", "淡绿", "柔灰")
        ReaderTheme.entries.zip(labels).forEach { (theme, label) ->
            composeRule.onNodeWithText(label).assertExists()
            composeRule.onNodeWithTag("reader-theme-${theme.name}").performScrollTo().performClick()
            composeRule.runOnIdle { assertEquals(theme, settings.value.theme) }
            ReaderTheme.entries.forEach { candidate ->
                val node = composeRule.onNodeWithTag("reader-theme-${candidate.name}")
                if (candidate == theme) node.assertIsSelected() else node.assertIsNotSelected()
            }
        }
        composeRule.onNodeWithTag("reader-theme-SYSTEM").performScrollTo().performClick().assertIsSelected()
        composeRule.onNodeWithTag("reader-theme-GRAY").assertIsNotSelected()
    }

    @Test
    fun verticalReaderRendersTextAndMissingImagePlaceholder() {
        var toggleCount = 0
        composeRule.setContent {
            MaterialTheme {
                VerticalReader(
                    content = ReaderContent(
                        listOf(
                            ReaderBlock.Text("原生纵向正文"),
                            ReaderBlock.Image(null, "缺失插图", null),
                        ),
                    ),
                    settings = ReaderSettings(),
                    palette = palette,
                    chapterTitle = "测试章节",
                    initialFraction = 0f,
                    fragment = null,
                    jumpToken = 0,
                    hasPreviousChapter = false,
                    hasNextChapter = false,
                    onToggleControls = { toggleCount++ },
                    onProgress = {},
                    onFragmentConsumed = {},
                    onPreviousChapter = {},
                    onNextChapter = {},
                )
            }
        }

        composeRule.onNodeWithText("原生纵向正文").fetchSemanticsNode()
        composeRule.onNodeWithText("缺失插图").fetchSemanticsNode()
        composeRule.onRoot().performTouchInput { click(center) }
        composeRule.runOnIdle { assertEquals(1, toggleCount) }
    }

    @Test
    fun pagedReaderRendersTextWithComposePager() {
        var toggleCount = 0
        var nextChapterCount = 0
        composeRule.setContent {
            MaterialTheme {
                PagedReader(
                    content = ReaderContent(listOf(ReaderBlock.Text("原生横向正文"))),
                    settings = ReaderSettings(readingMode = ReadingMode.PAGED),
                    palette = palette,
                    chapterTitle = "测试章节",
                    initialFraction = 0f,
                    fragment = null,
                    jumpToken = 0,
                    hasPreviousChapter = false,
                    hasNextChapter = true,
                    onToggleControls = { toggleCount++ },
                    onProgress = {},
                    onFragmentConsumed = {},
                    onPreviousChapter = {},
                    onNextChapter = { nextChapterCount++ },
                )
            }
        }

        composeRule.onNodeWithText("原生横向正文").fetchSemanticsNode()
        composeRule.onRoot().performTouchInput { click(center) }
        composeRule.onRoot().performTouchInput { click(Offset(width * 0.9f, center.y)) }
        composeRule.runOnIdle {
            assertEquals(1, toggleCount)
            assertEquals(1, nextChapterCount)
        }
    }

    @Test
    fun smoothReaderAnimatesAcrossChaptersInBothDirections() {
        assertChapterTurnsAnimate(ReadingMode.PAGED)
    }

    @Test
    fun flipReaderAnimatesAcrossChaptersInBothDirections() {
        assertChapterTurnsAnimate(ReadingMode.FLIP)
    }

    private fun assertChapterTurnsAnimate(mode: ReadingMode) {
        val chapterIndex = mutableStateOf(1)
        var chapterChanges = 0
        // Identical bodies deliberately exercise chapter identity independently of content equality.
        val chapters = (0..2).map { ReaderChapterContent("章节 $it", ReaderContent(listOf(ReaderBlock.Text("短章节正文")))) }
        composeRule.setContent {
            Box(Modifier.testTag("chapter-reader")) {
                MaterialTheme {
                    val index = chapterIndex.value
                    val previous = { chapterChanges++; chapterIndex.value = index - 1 }
                    val next = { chapterChanges++; chapterIndex.value = index + 1 }
                    if (mode == ReadingMode.PAGED) {
                        PagedReader(
                            content = chapters[index].content,
                            chapterTitle = chapters[index].title,
                            chapterKey = index.toString(),
                            previousChapter = chapters.getOrNull(index - 1),
                            nextChapter = chapters.getOrNull(index + 1),
                            settings = ReaderSettings(readingMode = mode),
                            palette = palette,
                            initialFraction = 0f,
                            fragment = null,
                            jumpToken = 0,
                            hasPreviousChapter = index > 0,
                            hasNextChapter = index < chapters.lastIndex,
                            onToggleControls = {},
                            onProgress = {},
                            onFragmentConsumed = {},
                            onPreviousChapter = previous,
                            onNextChapter = next,
                        )
                    } else {
                        FlipReader(
                            content = chapters[index].content,
                            chapterTitle = chapters[index].title,
                            chapterKey = index.toString(),
                            previousChapter = chapters.getOrNull(index - 1),
                            nextChapter = chapters.getOrNull(index + 1),
                            settings = ReaderSettings(readingMode = mode),
                            palette = palette,
                            initialFraction = 0f,
                            fragment = null,
                            jumpToken = 0,
                            hasPreviousChapter = index > 0,
                            hasNextChapter = index < chapters.lastIndex,
                            onToggleControls = {},
                            onProgress = {},
                            onFragmentConsumed = {},
                            onPreviousChapter = previous,
                            onNextChapter = next,
                        )
                    }
                }
            }
        }

        // Cross forward, back, and back again to exercise rebasing and both ends of the book.
        listOf(2, 1, 0).forEachIndexed { turn, target ->
            composeRule.waitForIdle()
            val origin = chapterIndex.value
            composeRule.mainClock.autoAdvance = false
            composeRule.onNodeWithTag("chapter-reader").performTouchInput {
                click(Offset(width * if (target > origin) 0.9f else 0.1f, center.y))
            }
            composeRule.mainClock.advanceTimeBy(32)
            composeRule.runOnIdle {
                assertEquals("Chapter must stay unchanged while the page is turning", origin, chapterIndex.value)
                assertEquals(turn, chapterChanges)
            }
            composeRule.mainClock.advanceTimeBy(1500)
            composeRule.mainClock.autoAdvance = true
            composeRule.waitForIdle()
            composeRule.runOnIdle {
                assertEquals(target, chapterIndex.value)
                assertEquals(turn + 1, chapterChanges)
            }
        }

        listOf(1, 2, 1).forEachIndexed { turn, target ->
            val forward = target > chapterIndex.value
            val reader = composeRule.onNodeWithTag("chapter-reader")
            if (mode == ReadingMode.FLIP) {
                // PTQ uses wall-clock time for its 70 ms drag threshold, not event timestamps.
                reader.performTouchInput {
                    down(Offset(width * if (forward) 0.85f else 0.15f, height * 0.75f))
                    moveTo(Offset(width * if (forward) 0.75f else 0.25f, height * 0.75f))
                }
                android.os.SystemClock.sleep(100)
                reader.performTouchInput {
                    moveTo(Offset(width * if (forward) 0.15f else 0.85f, height * 0.75f))
                    up()
                }
            } else {
                reader.performTouchInput {
                    swipe(
                        start = Offset(width * if (forward) 0.85f else 0.15f, height * 0.75f),
                        end = Offset(width * if (forward) 0.15f else 0.85f, height * 0.75f),
                        durationMillis = 400,
                    )
                }
            }
            composeRule.waitForIdle()
            composeRule.runOnIdle {
                assertEquals(target, chapterIndex.value)
                assertEquals(turn + 4, chapterChanges)
            }
        }
    }

    @Test
    fun verticalReaderMarksActiveTtsSentenceAsSelected() {
        composeRule.setContent {
            MaterialTheme {
                VerticalReader(
                    content = ReaderContent(listOf(ReaderBlock.Text("第一句。第二句。"))),
                    settings = ReaderSettings(),
                    palette = palette,
                    chapterTitle = "测试章节",
                    initialFraction = 0f,
                    fragment = null,
                    jumpToken = 0,
                    hasPreviousChapter = false,
                    hasNextChapter = false,
                    onToggleControls = {},
                    onProgress = {},
                    onFragmentConsumed = {},
                    onPreviousChapter = {},
                    onNextChapter = {},
                    activeSentence = TtsSentenceLocator(0, 0, 0, 4),
                )
            }
        }

        composeRule.onNodeWithText("第一句。第二句。").assertIsSelected()
    }

    @Test
    fun pagedReaderMarksActiveTtsSentenceAsSelected() {
        composeRule.setContent {
            MaterialTheme {
                PagedReader(
                    content = ReaderContent(listOf(ReaderBlock.Text("分页第一句。分页第二句。"))),
                    settings = ReaderSettings(readingMode = ReadingMode.PAGED),
                    palette = palette,
                    chapterTitle = "测试章节",
                    initialFraction = 0f,
                    fragment = null,
                    jumpToken = 0,
                    hasPreviousChapter = false,
                    hasNextChapter = false,
                    onToggleControls = {},
                    onProgress = {},
                    onFragmentConsumed = {},
                    onPreviousChapter = {},
                    onNextChapter = {},
                    activeSentence = TtsSentenceLocator(0, 0, 0, 6),
                )
            }
        }

        composeRule.onNodeWithText("分页第一句。分页第二句。").assertIsSelected()
    }

    @Test
    fun verticalReaderBoundaryPullActivatesPreviousAndNextChapter() {
        var previousChapterCount = 0
        var nextChapterCount = 0
        composeRule.setContent {
            MaterialTheme {
                VerticalReader(
                    content = ReaderContent(listOf(ReaderBlock.Text("短章节"))),
                    settings = ReaderSettings(),
                    palette = palette,
                    chapterTitle = "测试章节",
                    initialFraction = 0f,
                    fragment = null,
                    jumpToken = 0,
                    hasPreviousChapter = true,
                    hasNextChapter = true,
                    onToggleControls = {},
                    onProgress = {},
                    onFragmentConsumed = {},
                    onPreviousChapter = { previousChapterCount++ },
                    onNextChapter = { nextChapterCount++ },
                )
            }
        }

        composeRule.onNodeWithText("上一章").performClick()
        composeRule.onRoot().performTouchInput {
            swipe(
                start = Offset(center.x, height * 0.2f),
                end = Offset(center.x, height * 0.9f),
                durationMillis = 600,
            )
        }
        composeRule.onRoot().performTouchInput {
            swipe(
                start = Offset(center.x, height * 0.8f),
                end = Offset(center.x, height * 0.1f),
                durationMillis = 600,
            )
        }

        composeRule.runOnIdle {
            assertEquals(2, previousChapterCount)
            assertEquals(1, nextChapterCount)
        }
    }

    @Test
    fun verticalReaderReverseDragCancelsArmedPull() {
        var previousChapterCount = 0
        var nextChapterCount = 0
        composeRule.setContent {
            MaterialTheme {
                VerticalReader(
                    content = ReaderContent(listOf(ReaderBlock.Text("长章节".repeat(4_000)))),
                    settings = ReaderSettings(),
                    palette = palette,
                    chapterTitle = "测试章节",
                    initialFraction = 0f,
                    fragment = null,
                    jumpToken = 0,
                    hasPreviousChapter = true,
                    hasNextChapter = true,
                    onToggleControls = {},
                    onProgress = {},
                    onFragmentConsumed = {},
                    onPreviousChapter = { previousChapterCount++ },
                    onNextChapter = { nextChapterCount++ },
                )
            }
        }

        composeRule.onRoot().performTouchInput {
            down(Offset(center.x, height * 0.25f))
            moveTo(Offset(center.x, height * 0.9f), delayMillis = 500)
            moveTo(Offset(center.x, height * 0.45f), delayMillis = 500)
            up()
        }

        composeRule.runOnIdle {
            assertEquals(0, previousChapterCount)
            assertEquals(0, nextChapterCount)
        }
    }

    @Test
    fun armedChapterNavigationExposesReleaseSemantics() {
        composeRule.setContent {
            MaterialTheme {
                ChapterNavigation(
                    label = "上一章",
                    enabled = true,
                    onClick = {},
                    palette = palette,
                    edge = ChapterPullEdge.PREVIOUS,
                    pullProgress = 1f,
                    armed = true,
                )
            }
        }

        val node = composeRule.onNodeWithTag("chapter-navigation-previous").fetchSemanticsNode()
        assertEquals("松手切换上一章", node.config[SemanticsProperties.StateDescription])
    }
}
