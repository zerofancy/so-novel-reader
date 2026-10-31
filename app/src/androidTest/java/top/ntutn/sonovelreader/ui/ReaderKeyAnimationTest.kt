package top.ntutn.sonovelreader.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.v2.createComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import top.ntutn.sonovelreader.data.ReaderBlock
import top.ntutn.sonovelreader.data.ReaderContent
import top.ntutn.sonovelreader.data.ReaderSettings

class ReaderKeyAnimationTest {
    @get:Rule val rule = createComposeRule()

    @Test fun pagedKeysAnimateDropBusyCommandsAndCancel() = verify(flip = false)
    @Test fun flipKeysAnimateDropBusyCommandsAndCancel() = verify(flip = true)

    private fun verify(flip: Boolean) {
        val turner = ReaderPageTurner()
        val display = mutableStateOf<ReaderDisplayInfo?>(null)
        val palette = ReaderPalette(Color.White, Color.Black, Color.Gray, Color.LightGray)
        val content = ReaderContent((1..60).map { ReaderBlock.Text("第 $it 段，验证按键翻页动画以及取消操作时的阅读位置。") })
        rule.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalReaderPageTurner provides turner) {
                    if (flip) FlipReader(content, ReaderSettings(), palette, "动画测试", 0f, null, 0,
                        false, false, {}, {}, {}, {}, {}, onDisplayInfo = { display.value = it })
                    else PagedReader(content, ReaderSettings(), palette, "动画测试", 0f, null, 0,
                        false, false, {}, {}, {}, {}, {}, onDisplayInfo = { display.value = it })
                }
            }
        }
        rule.waitUntil(10000) { turner.ready() && display.value?.page == 1 }
        rule.waitForIdle()
        rule.mainClock.autoAdvance = false
        rule.runOnIdle {
            turner.turn(true)
            turner.turn(true)
        }
        rule.mainClock.advanceTimeBy(32)
        rule.runOnIdle { assertEquals("Location commits after animation", 1, display.value?.page) }
        rule.mainClock.advanceTimeBy(1500)
        rule.mainClock.autoAdvance = true
        rule.waitForIdle()
        rule.runOnIdle { assertEquals("Busy command must be dropped", 2, display.value?.page) }

        rule.mainClock.autoAdvance = false
        rule.runOnIdle { turner.turn(true) }
        rule.mainClock.advanceTimeBy(32)
        rule.runOnIdle { turner.cancel() }
        rule.mainClock.advanceTimeBy(1500)
        rule.mainClock.autoAdvance = true
        rule.waitForIdle()
        rule.runOnIdle { assertEquals("Canceled animation must not commit next page", 2, display.value?.page) }
        rule.runOnIdle { turner.turn(false) }
        rule.waitForIdle()
        rule.runOnIdle { assertEquals(1, display.value?.page) }
    }
}
