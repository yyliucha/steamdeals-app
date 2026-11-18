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
}
