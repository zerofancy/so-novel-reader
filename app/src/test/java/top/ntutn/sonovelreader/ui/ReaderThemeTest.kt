package top.ntutn.sonovelreader.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import top.ntutn.sonovelreader.data.ReaderTheme

class ReaderThemeTest {
    @Test
    fun fixedThemesHaveExpectedColorsRegardlessOfSystemTheme() {
        val expected = mapOf(
            ReaderTheme.LIGHT to ReaderPalette(Color(0xFFFAF8F3), Color(0xFF25231F), Color(0xFF6D685E), Color(0xFFE8E3D9)),
            ReaderTheme.DARK to ReaderPalette(Color(0xFF171717), Color(0xFFE7E2D8), Color(0xFFAAA49A), Color(0xFF292826)),
            ReaderTheme.SEPIA to ReaderPalette(Color(0xFFF2E8CF), Color(0xFF43392A), Color(0xFF786A55), Color(0xFFE3D5B5)),
            ReaderTheme.LINEN to ReaderPalette(Color(0xFFFAF0E6), Color(0xFF5C4033), Color(0xFF80695C), Color(0xFFE9DCCE)),
            ReaderTheme.GREEN to ReaderPalette(Color(0xFFC7EDCC), Color(0xFF2F4F4F), Color(0xFF4E6954), Color(0xFFACD5B2)),
            ReaderTheme.GRAY to ReaderPalette(Color(0xFFECECEC), Color(0xFF333333), Color(0xFF686868), Color(0xFFD5D5D5)),
        )
        assertEquals(ReaderTheme.entries.toSet() - ReaderTheme.SYSTEM, expected.keys)
        expected.forEach { (theme, palette) ->
            assertEquals(palette, readerPalette(theme, darkSystem = false))
            assertEquals(palette, readerPalette(theme, darkSystem = true))
        }
    }

    @Test
    fun systemThemeFollowsLightAndDarkPalettes() {
        assertEquals(readerPalette(ReaderTheme.LIGHT, false), readerPalette(ReaderTheme.SYSTEM, false))
        assertEquals(readerPalette(ReaderTheme.DARK, true), readerPalette(ReaderTheme.SYSTEM, true))
    }

    @Test
    fun newPresetsHaveReadableTextContrast() {
        listOf(ReaderTheme.LINEN, ReaderTheme.GREEN, ReaderTheme.GRAY).forEach { theme ->
            val palette = readerPalette(theme, darkSystem = false)
            listOf(palette.foreground, palette.muted).forEach { color ->
                val background = palette.background.luminance()
                val text = color.luminance()
                val contrast = (maxOf(background, text) + 0.05f) / (minOf(background, text) + 0.05f)
                assertTrue("$theme text contrast is $contrast", contrast >= 4.5f)
            }
        }
    }
}
