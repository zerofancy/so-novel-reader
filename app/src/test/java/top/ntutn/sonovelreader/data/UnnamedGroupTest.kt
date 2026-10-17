package top.ntutn.sonovelreader.data

import org.junit.Assert.assertEquals
import org.junit.Test

class UnnamedGroupTest {
    @Test fun usesFirstAvailableNameIncludingNumberingGaps() {
        assertEquals("未命名分类", nextUnnamedGroupName(emptySet()))
        assertEquals("未命名分类", nextUnnamedGroupName(setOf("未命名分类 2")))
        assertEquals("未命名分类 2", nextUnnamedGroupName(setOf("未命名分类")))
        assertEquals("未命名分类 3", nextUnnamedGroupName(setOf("未命名分类", "未命名分类 2", "未命名分类 4")))
    }
}
