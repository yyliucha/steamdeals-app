package cn.yyliucha.steamdeals

import cn.yyliucha.steamdeals.data.api.LatestResponse
import cn.yyliucha.steamdeals.ui.update.UpdateManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * DK（站点 TODO DK B）单测：检查更新决策与 apkPath 解析（纯函数，JVM 直测）——
 * apkPath 缺失/非法 → Unavailable（提示到站点 /app 页下载），不再笼统 Failed；
 * 正常 apkPath（站点相对路径 / GitHub 直链 / CDN）→ Available（不回归）；
 * 反向自检：把 decideUpdate 里 resolveApkUrl 为 null 的分支改回 Failed → 「apkPath 缺失」用例红；
 * 把 resolveApkUrl 白名单主机集合清空 → 「GitHub 直链可用」用例红。
 */
class UpdateManagerTest {

    private fun latest(
        version: String = "2.20.0",
        versionCode: Int? = 22000,
        minVersionCode: Int? = null,
        apkPath: String? = "/app/dl/steamdeals-2.20.0.apk",
    ) = LatestResponse(
        version = version,
        versionCode = versionCode,
        minVersionCode = minVersionCode,
        notes = listOf("修复 A"),
        apkPath = apkPath,
        sha256 = "abc123",
        size = 1024,
    )

    // ---- apkPath 缺失/非法 → Unavailable（DK B：不报「检查更新失败」） ----

    @Test
    fun `apkPath 缺失返回 Unavailable 而非 Failed`() {
        val r = UpdateManager.decideUpdate(latest(apkPath = null), currentVersionCode = 21900, ignoredVersionCode = 0)
        assertTrue("apkPath 缺失不得是 Failed（DK B：只提示到 /app 页下载）", r !is UpdateManager.CheckResult.Failed)
        assertEquals(UpdateManager.CheckResult.Unavailable("2.20.0"), r)
    }

    @Test
    fun `apkPath 空白返回 Unavailable`() {
        for (p in listOf("", "   ")) {
            val r = UpdateManager.decideUpdate(latest(apkPath = p), 21900, 0)
            assertTrue("apkPath=$p 应为 Unavailable", r is UpdateManager.CheckResult.Unavailable)
        }
    }

    @Test
    fun `非白名单主机直链返回 Unavailable`() {
        val r = UpdateManager.decideUpdate(latest(apkPath = "https://evil.example.com/steamdeals-2.20.0.apk"), 21900, 0)
        assertTrue("非白名单主机应拒绝 → Unavailable", r is UpdateManager.CheckResult.Unavailable)
    }

    // ---- 正常 apkPath → Available（不回归） ----

    @Test
    fun `站点相对路径补全为 https 站点地址`() {
        val r = UpdateManager.decideUpdate(latest(apkPath = "/app/dl/steamdeals-2.20.0.apk"), 21900, 0)
        assertTrue(r is UpdateManager.CheckResult.Available)
        assertEquals(
            "https://sd.yyliucha.cn/app/dl/steamdeals-2.20.0.apk",
            (r as UpdateManager.CheckResult.Available).info.apkUrl,
        )
    }

    @Test
    fun `GitHub 直链原样保留`() {
        val gh = "https://github.com/yyliucha/steamdeals-app/releases/download/app-v2.20.0/steamdeals-2.20.0.apk"
        val r = UpdateManager.decideUpdate(latest(apkPath = gh), 21900, 0)
        assertTrue(r is UpdateManager.CheckResult.Available)
        assertEquals(gh, (r as UpdateManager.CheckResult.Available).info.apkUrl)
    }

    @Test
    fun `GitHub CDN 主机白名单可用`() {
        val cdn = "https://release-assets.githubusercontent.com/steamdeals-2.20.0.apk"
        val r = UpdateManager.decideUpdate(latest(apkPath = cdn), 21900, 0)
        assertTrue("CDN 主机在白名单 → Available", r is UpdateManager.CheckResult.Available)
    }

    // ---- 无新版 / 忽略版本 / 强制更新 ----

    @Test
    fun `无新版或同版本返回 None`() {
        assertEquals(UpdateManager.CheckResult.None, UpdateManager.decideUpdate(latest(versionCode = null), 21900, 0))
        assertEquals(UpdateManager.CheckResult.None, UpdateManager.decideUpdate(latest(versionCode = 21900), 21900, 0))
        assertEquals(UpdateManager.CheckResult.None, UpdateManager.decideUpdate(latest(versionCode = 21800), 21900, 0))
    }

    @Test
    fun `忽略版本跳过但强制更新不跳过`() {
        assertEquals(
            UpdateManager.CheckResult.None,
            UpdateManager.decideUpdate(latest(versionCode = 22000), 21900, ignoredVersionCode = 22000),
        )
        val r = UpdateManager.decideUpdate(latest(versionCode = 22000, minVersionCode = 22000), 21900, ignoredVersionCode = 22000)
        assertTrue(r is UpdateManager.CheckResult.Available && r.force)
    }

    // ---- resolveApkUrl 单元 ----

    @Test
    fun `resolveApkUrl 空与非白名单主机返回 null`() {
        assertNull(UpdateManager.resolveApkUrl(null))
        assertNull(UpdateManager.resolveApkUrl(""))
        assertNull(UpdateManager.resolveApkUrl("https://evil.example.com/x.apk"))
    }

    // ---- DR（站点 TODO DR，docs/APP-INSTANT-UPDATE.md §3.2）：自动检查节流 24h → 1h；手动检查不变 ----

    @Test
    fun `THROTTLE_MS 为 1 小时（DR：24h 缩短到 1h）`() {
        assertEquals("自动检查节流窗口必须是 1 小时", 60 * 60 * 1000L, UpdateManager.THROTTLE_MS)
    }

    @Test
    fun `自动检查 1 小时内不重复请求（DR：24h 内也不查，现在 1h 内不查）`() {
        val now = 1_000_000_000L
        assertTrue("刚检查过 → 节流", UpdateManager.isThrottled(false, now, now))
        assertTrue("距上次 1 分钟 → 节流", UpdateManager.isThrottled(false, now, now - 60_000L))
        assertTrue("距上次 59 分钟 → 节流", UpdateManager.isThrottled(false, now, now - 59 * 60 * 1000L))
    }

    @Test
    fun `自动检查超过 1 小时放行（含恰好 1 小时与从未检查）`() {
        val now = 1_000_000_000L
        assertEquals("恰好 1 小时 → 放行", false, UpdateManager.isThrottled(false, now, now - 60 * 60 * 1000L))
        assertEquals("超过 1 小时 → 放行", false, UpdateManager.isThrottled(false, now, now - 61 * 60 * 1000L))
        assertEquals("从未检查过（lastCheckAt=0）→ 放行", false, UpdateManager.isThrottled(false, now, 0L))
    }

    @Test
    fun `手动检查永不节流（BY 4-5 不回归：manual 不节流不写时间）`() {
        val now = 1_000_000_000L
        assertEquals("刚检查过再手动点 → 立即查", false, UpdateManager.isThrottled(true, now, now))
        assertEquals("手动检查无视节流窗口", false, UpdateManager.isThrottled(true, now, now - 1000L))
        assertEquals("手动首次检查 → 放行", false, UpdateManager.isThrottled(true, now, 0L))
    }
}
