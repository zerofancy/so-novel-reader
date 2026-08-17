package top.ntutn.sonovelreader.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.semantics.SemanticsProperties
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import top.ntutn.sonovelreader.data.ReaderBlock
import top.ntutn.sonovelreader.data.ReaderContent
import top.ntutn.sonovelreader.data.ReaderSettings
import top.ntutn.sonovelreader.data.ReadingMode
import top.ntutn.sonovelreader.tts.TtsSentenceLocator

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
    fun verticalReaderMarksActiveTtsSentenceAsSelected() {
        composeRule.setContent {
            MaterialTheme {
                VerticalReader(
                    content = ReaderContent(listOf(ReaderBlock.Text("第一句。第二句。"))),
                    settings = ReaderSettings(),
                    palette = palette,
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
