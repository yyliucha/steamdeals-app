package cn.yyliucha.steamdeals

import cn.yyliucha.steamdeals.ui.profile.initialOf
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * DQ 批（站点 docs/PROFILE-UI-2026-11-19.md 决策 2）：默认头像统一规则——昵称首字母英文大写 /
 * 第一个汉字；空昵称回退 'S'。与 Web 端 frontend/src/utils/avatar.js 的 avatarInitial 同口径
 * （Web 端测试：test/profile.test.js 静态锁 + avatarInitial 用例）。
 * 反向自检：把 initialOf 改回 substring(0,1) 对增补平面字符的用例会红（codePointAt 用例）。
 */
class ProfileInitialTest {

    @Test
    fun `英文昵称取首字母并大写`() {
        assertEquals("F", initialOf("frank"))
        assertEquals("F", initialOf("FF"))
        assertEquals("H", initialOf("Happy"))
    }

    @Test
    fun `中文昵称取第一个汉字`() {
        assertEquals("星", initialOf("星露谷玩家"))
        assertEquals("昵", initialOf("昵称测试"))
    }

    @Test
    fun `空昵称或空白回退 S`() {
        assertEquals("S", initialOf(null))
        assertEquals("S", initialOf(""))
        assertEquals("S", initialOf("   "))
    }

    @Test
    fun `首字符按码点取（增补平面字符安全，与 Web avatarInitial 同口径）`() {
        // U+1D11E 音乐符号（代理对）；按 UTF-16 单元切会得到半个代理符
        assertEquals("\uD834\uDD1E", initialOf("\uD834\uDD1Ename"))
    }
}
