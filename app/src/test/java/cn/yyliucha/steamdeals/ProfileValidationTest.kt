package cn.yyliucha.steamdeals

import cn.yyliucha.steamdeals.ui.profile.ProfileValidation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * CX 批（docs/USER-PROFILE-EDIT.md §2 #5/#10）单测：资料表单校验，与站点服务端同口径——
 * 昵称 2–20 / 简介 1–200；管理员可空；普通用户必填。
 * 反向自检：把长度上限 20 改成 21 → 「21 字昵称拒绝」用例红；把 isAdmin 豁免删掉 → 「管理员空昵称」用例红。
 */
class ProfileValidationTest {

    @Test
    fun `昵称 2-20 边界`() {
        assertNull(ProfileValidation.nicknameError("ab", isAdmin = false))
        assertNull(ProfileValidation.nicknameError("昵称测试", isAdmin = false))
        assertNull(ProfileValidation.nicknameError("十".repeat(20), isAdmin = false))
        assertEquals("昵称需要 2–20 个字符", ProfileValidation.nicknameError("a", isAdmin = false))
        assertEquals("昵称需要 2–20 个字符", ProfileValidation.nicknameError("十".repeat(21), isAdmin = false))
    }

    @Test
    fun `昵称首尾空白先裁剪再判长`() {
        assertNull(ProfileValidation.nicknameError("  昵称  ", isAdmin = false))
        assertEquals("昵称需要 2–20 个字符", ProfileValidation.nicknameError("  单  ", isAdmin = false))
    }

    @Test
    fun `管理员昵称可空`() {
        assertNull(ProfileValidation.nicknameError("", isAdmin = true))
        assertEquals("昵称需要 2–20 个字符", ProfileValidation.nicknameError("a", isAdmin = true))
    }

    @Test
    fun `简介 1-200 边界与普通用户必填`() {
        assertNull(ProfileValidation.bioError("一句话简介", isAdmin = false))
        assertNull(ProfileValidation.bioError("字".repeat(200), isAdmin = false))
        assertEquals("个人简介必填（1–200 字）", ProfileValidation.bioError("", isAdmin = false))
        assertEquals("个人简介必填（1–200 字）", ProfileValidation.bioError("   ", isAdmin = false))
        assertEquals("个人简介不能超过 200 字", ProfileValidation.bioError("字".repeat(201), isAdmin = false))
    }

    @Test
    fun `管理员简介可空`() {
        assertNull(ProfileValidation.bioError("", isAdmin = true))
        assertEquals("个人简介不能超过 200 字", ProfileValidation.bioError("字".repeat(201), isAdmin = true))
    }

    // ---- CZ 批（docs/EMAIL-CHANGE.md）：换邮箱表单校验 ----

    @Test
    fun `换邮箱新邮箱格式与服务端同口径`() {
        assertNull(ProfileValidation.emailChangeEmailError("new-mail@example.com"))
        assertNull("首尾空白裁剪", ProfileValidation.emailChangeEmailError("  User@Example.COM  "))
        assertEquals("请输入新邮箱", ProfileValidation.emailChangeEmailError(""))
        assertEquals("请输入新邮箱", ProfileValidation.emailChangeEmailError("   "))
        assertEquals("邮箱格式不正确", ProfileValidation.emailChangeEmailError("not-an-email"))
        assertEquals("邮箱格式不正确", ProfileValidation.emailChangeEmailError("a@b"))
    }

    @Test
    fun `换邮箱验证码必须 6 位数字`() {
        assertNull(ProfileValidation.emailChangeCodeError("123456"))
        assertNull("首尾空白裁剪", ProfileValidation.emailChangeCodeError(" 842193 "))
        assertEquals("请输入 6 位验证码", ProfileValidation.emailChangeCodeError(""))
        assertEquals("请输入 6 位验证码", ProfileValidation.emailChangeCodeError("12345"))
        assertEquals("请输入 6 位验证码", ProfileValidation.emailChangeCodeError("1234567"))
        assertEquals("请输入 6 位验证码", ProfileValidation.emailChangeCodeError("abcdef"))
    }
}
