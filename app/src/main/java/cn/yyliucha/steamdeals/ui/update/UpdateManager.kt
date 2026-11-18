package cn.yyliucha.steamdeals.ui.update

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import cn.yyliucha.steamdeals.BuildConfig
import cn.yyliucha.steamdeals.data.api.ApiClient
import cn.yyliucha.steamdeals.data.api.LatestResponse
import cn.yyliucha.steamdeals.data.api.SteamDealsApi
import cn.yyliucha.steamdeals.data.store.SessionStore
import java.io.File
import java.io.FileInputStream

/**
 * App 内置更新（CA-4，沿用 docs/MOBILE-APP-UPDATE.md 的交互与元数据契约，原生实现）：
 * latest.json + versionCode/notes/minVersionCode、启动+回前台（24h 节流）+ 手动检查、
 * Wi-Fi 默认、SHA-256 校验、系统安装确认、站点优先 / GitHub Release 兜底。
 */
class UpdateManager(
    private val context: Context,
    private val api: SteamDealsApi,
    private val client: ApiClient,
    private val store: SessionStore,
) {
    data class UpdateInfo(
        val version: String,
        val versionCode: Int,
        val minVersionCode: Int?,
        val notes: List<String>,
        val apkUrl: String,
        val sha256: String?,
        val size: Long?,
    )

    sealed class CheckResult {
        object None : CheckResult()
        data class Available(val info: UpdateInfo, val force: Boolean) : CheckResult()
        /** DK（站点 TODO DK B）：有新版但下载地址暂不可用（apkPath 缺失/非法）——提示到站点 /app 页下载。 */
        data class Unavailable(val version: String) : CheckResult()
        object Failed : CheckResult()
    }

    companion object {
        const val THROTTLE_MS = 24 * 60 * 60 * 1000L
        const val GITHUB_HOST = "github.com"
        val GITHUB_CDN_HOSTS = setOf("objects.githubusercontent.com", "release-assets.githubusercontent.com")
        val SITE_HOSTS = setOf("sd.yyliucha.cn")
        // 本机联调（模拟器 10.0.2.2 / 真机局域网 IP）仅 debug 放行
        val DEBUG_HOSTS = setOf("10.0.2.2", "127.0.0.1", "localhost")

        /**
         * DK 批（站点 TODO DK B）：检查结果决策抽成纯函数（JVM 单测直测）——
         * apkPath 缺失/非法不再笼统 Failed，返回 Unavailable（提示到站点 /app 页下载）；
         * 网络失败仍是 Failed（真「检查更新失败」只留给请求本身出错）。
         */
        fun decideUpdate(latest: LatestResponse, currentVersionCode: Int, ignoredVersionCode: Int): CheckResult {
            val code = latest.versionCode ?: return CheckResult.None
            if (code <= currentVersionCode) return CheckResult.None
            val force = (latest.minVersionCode ?: 0) > currentVersionCode
            if (!force && code == ignoredVersionCode) return CheckResult.None
            val url = resolveApkUrl(latest.apkPath)
                ?: return CheckResult.Unavailable(latest.version ?: "v$code")
            val notes = latest.notes.orEmpty().ifEmpty { listOfNotNull(latest.version) }
            return CheckResult.Available(
                info = UpdateInfo(
                    version = latest.version ?: "v$code",
                    versionCode = code,
                    minVersionCode = latest.minVersionCode,
                    notes = notes,
                    apkUrl = url,
                    sha256 = latest.sha256,
                    size = latest.size,
                ),
                force = force,
            )
        }

        /**
         * 站点优先、GitHub Release 兜底；只允许 https + 白名单主机（BY §4.3）。
         * 用 JDK java.net.URI 解析（JVM 单测可直接跑，不依赖 android.net.Uri 的 mock）。
         */
        fun resolveApkUrl(apkPath: String?): String? {
            val raw = apkPath?.trim().orEmpty()
            if (raw.isEmpty()) return null
            val url: String = if (raw.startsWith("http://") || raw.startsWith("https://")) {
                raw
            } else {
                "https://sd.yyliucha.cn" + if (raw.startsWith("/")) raw else "/$raw"
            }
            val uri = try {
                java.net.URI(url)
            } catch (_: Exception) {
                return null
            }
            val scheme = uri.scheme ?: return null
            if (scheme != "https" && !(BuildConfig.DEBUG && scheme == "http")) return null
            val host = uri.host ?: return null
            val allowed = SITE_HOSTS + GITHUB_CDN_HOSTS + setOf(GITHUB_HOST)
            val debugAllowed = BuildConfig.DEBUG && host in DEBUG_HOSTS
            if (host !in allowed && !debugAllowed) return null
            return url
        }
    }

    /** 检查更新。manual=true 忽略 24h 节流且不写检查时间（BY §4.5）。 */
    suspend fun check(
        manual: Boolean,
        currentVersionCode: Int = BuildConfig.VERSION_CODE,
        ignoredVersionCode: Int = 0,
        lastCheckAt: Long = 0,
    ): CheckResult {
        if (!manual && System.currentTimeMillis() - lastCheckAt < THROTTLE_MS) return CheckResult.None
        if (!manual) store.setLastUpdateCheckAt(System.currentTimeMillis())

        val latest = try {
            api.latest()
        } catch (_: Exception) {
            return CheckResult.Failed
        }
        return decideUpdate(latest, currentVersionCode, ignoredVersionCode)
    }

    fun isWifi(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return true
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return true
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
    }

    fun apkFile(): File = File(context.cacheDir, "apk/update.apk")

    /** 下载 + SHA-256 校验。返回校验通过的 APK 文件；不匹配 → 删除并抛 ApiException。 */
    suspend fun downloadAndVerify(
        info: UpdateInfo,
        onProgress: (Long, Long) -> Unit,
    ): File {
        val dest = apkFile()
        dest.parentFile?.mkdirs()
        val (file, sha256) = client.download(info.apkUrl, dest, onProgress)
        val expected = info.sha256?.trim()?.lowercase()
        if (!expected.isNullOrBlank() && sha256 != expected) {
            file.delete()
            throw java.lang.IllegalStateException("sha256 mismatch: $sha256 != $expected")
        }
        return file
    }

    /** 系统安装确认：PackageInstaller 会话优先，失败回落 ACTION_VIEW（BY §4.4）。 */
    fun install(apk: File): Boolean {
        return try {
            installWithPackageInstaller(apk)
        } catch (_: Exception) {
            installWithActionView(apk)
        }
    }

    private fun installWithPackageInstaller(apk: File): Boolean {
        val pm = context.packageManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !pm.canRequestPackageInstalls()) {
            requestInstallPermission()
            return false
        }
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        val sessionId = pm.packageInstaller.createSession(params)
        pm.packageInstaller.openSession(sessionId).use { session ->
            FileInputStream(apk).use { input ->
                session.openWrite("update.apk", 0, apk.length()).use { out ->
                    input.copyTo(out)
                    session.fsync(out)
                }
            }
            val intent = Intent(context, cn.yyliucha.steamdeals.ui.update.InstallResultReceiver::class.java)
            val pi = android.app.PendingIntent.getBroadcast(
                context,
                sessionId,
                intent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE,
            )
            session.commit(pi.intentSender)
        }
        return true
    }

    private fun installWithActionView(apk: File): Boolean {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apk,
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        return true
    }

    /** 引导允许「安装未知应用」。 */
    fun requestInstallPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val intent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}"),
            )
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
    }

    /** GitHub Release 兜底链接（站点不可达时给「打开发布页」用）。 */
    fun releasePageUrl(version: String): String =
        "https://${GITHUB_HOST}/yyliucha/steamdeals/releases/tag/app-v$version"
}
