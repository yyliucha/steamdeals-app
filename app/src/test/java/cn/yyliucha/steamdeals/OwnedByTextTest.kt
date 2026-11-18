package cn.yyliucha.steamdeals

import cn.yyliucha.steamdeals.data.api.OwnedByDto
import cn.yyliucha.steamdeals.ui.components.ownedByFullText
import cn.yyliucha.steamdeals.ui.components.ownedByText
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * CV 批（docs/APP-SEARCH-OWNED.md 决策 3）单测：已拥有账号徽标文案，与 Web ownedByText 同口径——
 * 前 2 个账号名 +N；名字缺失回退 steamId；全空显示「已拥有」。
 * 反向自检：把 `names.size > 2` 改成 `> 3` → 「三个账号 +N」用例红。
 */
class OwnedByTextTest {

    private fun by(names: List<Pair<String?, String?>>) =
        names.map { OwnedByDto(steamId = it.first, name = it.second) }

    @Test
    fun `单账号显示账号名`() {
        assertEquals(
            "已拥有 · Alice 主号",
            ownedByText(by(listOf("76561198000000001" to "Alice 主号"))),
        )
    }

    @Test
    fun `两账号全列`() {
        assertEquals(
            "已拥有 · Alice 主号、Bob 小号",
            ownedByText(by(listOf("1" to "Alice 主号", "2" to "Bob 小号"))),
        )
    }

    @Test
    fun `超过两个账号前 2 加 N`() {
        assertEquals(
            "已拥有 · Alice 主号、Bob 小号 +2",
            ownedByText(by(listOf("1" to "Alice 主号", "2" to "Bob 小号", "3" to "C", "4" to "D"))),
        )
    }

    @Test
    fun `名字缺失回退 steamId`() {
        assertEquals(
            "已拥有 · 76561198000000099",
            ownedByText(by(listOf("76561198000000099" to null))),
        )
    }

    @Test
    fun `全空显示已拥有`() {
        assertEquals("已拥有", ownedByText(emptyList()))
        assertEquals("已拥有", ownedByText(by(listOf(null to "  "))))
    }

    @Test
    fun `详情列全——超过两个账号全部列出（CV 决策 3 详情口径）`() {
        assertEquals(
            "已拥有 · Alice 主号、Bob 小号、C 号、D 号",
            ownedByFullText(by(listOf("1" to "Alice 主号", "2" to "Bob 小号", "3" to "C 号", "4" to "D 号"))),
        )
        assertEquals("已拥有", ownedByFullText(emptyList()))
    }
}
