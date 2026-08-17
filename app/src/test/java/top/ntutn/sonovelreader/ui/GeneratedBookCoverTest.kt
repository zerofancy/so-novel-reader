package top.ntutn.sonovelreader.ui

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GeneratedBookCoverTest {
    @Test
    fun sameTitleAlwaysProducesSameStyle() {
        assertEquals(generatedCoverStyle("三体"), generatedCoverStyle("三体"))
    }

    @Test
    fun representativeTitlesProduceDifferentBackgrounds() {
        val backgrounds = listOf("三体", "活着", "1984", "The Hobbit")
            .map { generatedCoverStyle(it).background }

        assertEquals(backgrounds.size, backgrounds.distinct().size)
        assertNotEquals(generatedCoverStyle("三体").background, generatedCoverStyle("活着").background)
    }

    @Test
    fun backgroundHslComponentsStayWithinConfiguredRanges() {
        listOf("三体", "活着", "1984", "The Hobbit", "百年孤独", "局外人").forEach { title ->
            val hsl = generatedCoverStyle(title).background.toHsl()

            assertTrue("saturation for $title was ${hsl[1]}", hsl[1] in 0.415f..0.585f)
            assertTrue("lightness for $title was ${hsl[2]}", hsl[2] in 0.355f..0.485f)
        }
    }

    @Test
    fun generatedColorsAlwaysMeetMinimumTextContrast() {
        repeat(10_000) { index ->
            val style = generatedCoverStyle("书名-$index-${index * 31}")
            assertTrue(
                "contrast for title $index was ${contrastRatio(style.background, style.foreground)}",
                contrastRatio(style.background, style.foreground) >= 4.5f,
            )
        }
    }

    @Test
    fun extractsFirstHanCharacterBeforeUsingFallback() {
        assertEquals("三", firstCoverCharacter("《三体》"))
        assertEquals("汉", firstCoverCharacter("ABC · 汉字"))
        assertEquals("扩", firstCoverCharacter("🚀《扩展》"))
        assertEquals("𠀀", firstCoverCharacter("【𠀀】扩展区"))
    }

    @Test
    fun fallsBackToFirstNonWhitespaceUnicodeCharacter() {
        assertEquals("T", firstCoverCharacter("The Hobbit"))
        assertEquals("1", firstCoverCharacter("  1984"))
        assertEquals("🚀", firstCoverCharacter("  🚀 Space"))
        assertEquals("书", firstCoverCharacter("   \n\t"))
        assertEquals("书", firstCoverCharacter(""))
    }
}

private fun Color.toHsl(): FloatArray {
    val redComponent = red
    val greenComponent = green
    val blueComponent = blue
    val maximum = maxOf(redComponent, greenComponent, blueComponent)
    val minimum = minOf(redComponent, greenComponent, blueComponent)
    val delta = maximum - minimum
    val lightness = (maximum + minimum) / 2f
    val saturation = if (delta == 0f) {
        0f
    } else {
        delta / (1f - kotlin.math.abs(2f * lightness - 1f))
    }

    return floatArrayOf(0f, saturation, lightness)
}
