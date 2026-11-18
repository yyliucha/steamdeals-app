package cn.yyliucha.steamdeals

import cn.yyliucha.steamdeals.data.api.ProfileRequests
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * CX 批（docs/USER-PROFILE-EDIT.md §3）单测：账号操作请求构造——路径 URL 编码、
 * 保存请求体字段名与 null 语义、头像地址派生（无来源 → 首字回退）。
 * 反向自检：把 PATH_PROFILE 改成 /api/app/me  → 路径断言红；把 profileBody 的 null 改成不发送 → JSON 断言红。
 */
class ProfileRequestsTest {

    @Test
    fun `账号路径做 URL 编码并按动作拼后缀`() {
        assertEquals(
            "/api/app/steam/accounts/76561198000000001",
            ProfileRequests.steamAccountPath("76561198000000001"),
        )
        assertEquals(
            "/api/app/steam/accounts/76561198000000001/default",
            ProfileRequests.steamAccountPath("76561198000000001", "default"),
        )
        // 特殊字符编码（防路径注入/错乱）
        assertTrue(ProfileRequests.steamAccountPath("a b/c", "default").contains("a+b%2Fc"))
    }

    @Test
    fun `保存资料请求体字段名与 null 语义（服务端 null=保持现值、空串=清空）`() {
        val json = ProfileRequests.profileBody("昵称", "简介", "dark", true)
        assertTrue(json.contains("\"displayName\":\"昵称\""))
        assertTrue(json.contains("\"bio\":\"简介\""))
        assertTrue(json.contains("\"theme\":\"dark\""))
        assertTrue(json.contains("\"emailAlerts\":true"))
        val clear = ProfileRequests.profileBody("", "", null, null)
        assertTrue("空串=管理员清空昵称", clear.contains("\"displayName\":\"\""))
        assertTrue(clear.contains("\"bio\":\"\""))
        assertTrue("null=跟随系统", clear.contains("\"theme\":null"))
        assertTrue("emailAlerts 未指定时不发送", !clear.contains("emailAlerts"))
    }

    @Test
    fun `条目查询参数：q 原样 + 分页`() {
        val q = ProfileRequests.steamItemsQuery(" 星露 ", 2, 10)
        assertEquals(" 星露 ", q["q"])
        assertEquals("2", q["page"])
        assertEquals("10", q["pageSize"])
    }

    @Test
    fun `头像地址：无来源返回 null（回退首字）；有来源带缓存破坏参数`() {
        assertNull(ProfileRequests.avatarUrl("https://sd.yyliucha.cn", 7, null, null))
        assertNull(ProfileRequests.avatarUrl("https://sd.yyliucha.cn", 7, "", "x"))
        assertEquals(
            "https://sd.yyliucha.cn/api/avatar/7?v=2026-11-18T00:00:00.000Z",
            ProfileRequests.avatarUrl("https://sd.yyliucha.cn", 7, "custom", "2026-11-18T00:00:00.000Z"),
        )
        assertEquals(
            "https://sd.yyliucha.cn/api/avatar/7?v=0",
            ProfileRequests.avatarUrl("https://sd.yyliucha.cn/", 7, "steam:76561198000000001", null),
        )
    }
}
