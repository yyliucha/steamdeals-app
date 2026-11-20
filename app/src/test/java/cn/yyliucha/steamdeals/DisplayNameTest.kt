package cn.yyliucha.steamdeals

import cn.yyliucha.steamdeals.ui.components.displayNameOf
import cn.yyliucha.steamdeals.ui.components.displayNameOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * EJ 批（站点 docs/DISPLAY-NAME-RULE.md §1/§4）：显示名 = 昵称（trim 后非空）? 昵称 : 用户名。
 * 「我的」页账号卡与资料页头像首字共用本口径（MeScreen / ProfileEditScreen）。
 * 反向自检：把实现改回 `displayName ?: username`（不做 trim / 空串判定）时，
 * 「空白昵称回退用户名」「清空后回退用户名」两组用例会红。
 */
class DisplayNameTest {

    @Test
    fun `有昵称显示昵称`() {
        assertEquals("星露谷玩家", displayNameOf("星露谷玩家", "alice"))
        assertEquals("Frank", displayNameOf("Frank", "frank001"))
    }

    @Test
    fun `昵称两端空白按 trim 后显示`() {
        assertEquals("昵称", displayNameOf("  昵称  ", "alice"))
        assertEquals("昵称", displayNameOrNull("  昵称  ", "alice"))
    }

    @Test
    fun `昵称为空或空白回退用户名`() {
        assertEquals("alice", displayNameOf(null, "alice"))
        assertEquals("alice", displayNameOf("", "alice"))
        assertEquals("alice", displayNameOf("   ", "alice"))
        assertEquals("alice", displayNameOrNull("   ", "alice"))
    }

    @Test
    fun `两者都没有时返回占位或 null`() {
        assertEquals("—", displayNameOf(null, null))
        assertEquals("—", displayNameOf(" ", ""))
        assertNull(displayNameOrNull(null, null))
        assertNull(displayNameOrNull("  ", "  "))
    }

    @Test
    fun `昵称与用户名不做并列拼接`() {
        val shown = displayNameOf("昵称", "alice")
        assertEquals("昵称", shown)
        assertEquals(false, shown.contains("alice"))
    }
}
