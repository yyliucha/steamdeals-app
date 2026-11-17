package cn.yyliucha.steamdeals

import cn.yyliucha.steamdeals.ui.components.nameCopyText
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * CM-4 单测：复制游戏名口径 = Web（frontend/src/utils/gameNames.js nameCopyText，AO 批）：
 * 中英文两行；缺英文/与中文相同 → 回退单行；缺中文 → 单行英文；全缺 → 空串；两侧空白去除。
 * 反向自检：把「en == zh 回退单行」分支删掉 → 「中英相同」用例红；
 *           把 trim 删掉 → 「两侧空白去除」用例红。
 */
class GameNameTextTest {

    @Test
    fun `中英两行`() {
        assertEquals("纪元1800\nAnno 1800", nameCopyText("纪元1800", "Anno 1800"))
    }

    @Test
    fun `缺英文回退单行中文`() {
        assertEquals("黑神话：悟空", nameCopyText("黑神话：悟空", null))
        assertEquals("黑神话：悟空", nameCopyText("黑神话：悟空", ""))
    }

    @Test
    fun `缺中文回退单行英文`() {
        assertEquals("Rust", nameCopyText(null, "Rust"))
        assertEquals("Rust", nameCopyText("", "Rust"))
    }

    @Test
    fun `中英相同回退单行`() {
        assertEquals("Dota 2", nameCopyText("Dota 2", "Dota 2"))
    }

    @Test
    fun `全缺返回空串`() {
        assertEquals("", nameCopyText(null, null))
        assertEquals("", nameCopyText("  ", ""))
    }

    @Test
    fun `两侧空白去除`() {
        assertEquals("纪元1800\nAnno 1800", nameCopyText(" 纪元1800 ", " Anno 1800 "))
    }
}
