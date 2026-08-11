package top.ntutn.sonovelreader.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import top.ntutn.sonovelreader.data.ReaderBlock
import top.ntutn.sonovelreader.data.ReaderContent
import top.ntutn.sonovelreader.data.ReaderSettings
import top.ntutn.sonovelreader.data.ReadingMode

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
}
