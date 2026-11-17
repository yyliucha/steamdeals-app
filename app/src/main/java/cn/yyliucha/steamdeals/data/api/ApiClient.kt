package cn.yyliucha.steamdeals.data.api

import cn.yyliucha.steamdeals.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * App 网络层（docs/APP-API.md）：
 * - 所有请求走 /api/app 命名空间（服务端去前缀路由，豁免黑名单/设备封禁）；
 * - 自动带 Authorization: Bearer <token> 与 X-Device-Id；
 * - 错误码统一映射：401 login_required / 403 must_change_password / 429 retryAfterSec / 其它。
 */
class ApiClient(
    private val baseUrl: String,
    private val tokenProvider: () -> String?,
    private val deviceIdProvider: () -> String?,
) {
    val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val builder = chain.request().newBuilder()
                .header("Accept", "application/json")
                .header("User-Agent", "SteamDealsApp/${BuildConfig.VERSION_NAME} (Android)")
            tokenProvider()?.let { builder.header("Authorization", "Bearer $it") }
            deviceIdProvider()?.let { builder.header("X-Device-Id", it) }
            chain.proceed(builder.build())
        }
        .build()

    private fun url(path: String, query: Map<String, String?>): String {
        val q = query.entries
            .filter { !it.value.isNullOrBlank() }
            .joinToString("&") { "${it.key}=${java.net.URLEncoder.encode(it.value, "UTF-8")}" }
        return if (q.isEmpty()) "$baseUrl$path" else "$baseUrl$path?$q"
    }

    suspend fun get(path: String, query: Map<String, String?> = emptyMap()): String =
        raw("GET", path, query, null)

    suspend fun post(path: String, body: String? = null): String =
        raw("POST", path, emptyMap(), body)

    suspend fun delete(path: String): String =
        raw("DELETE", path, emptyMap(), null)

    suspend fun raw(method: String, path: String, query: Map<String, String?>, body: String?): String =
        withContext(Dispatchers.IO) {
            val requestBuilder = Request.Builder().url(url(path, query))
            val req = when (method) {
                "POST" -> requestBuilder.post(
                    (body ?: "{}").toRequestBody("application/json; charset=utf-8".toMediaType()),
                )
                "DELETE" -> requestBuilder.delete()
                else -> requestBuilder.get()
            }.build()
            val response = try {
                client.newCall(req).execute()
            } catch (e: Exception) {
                android.util.Log.e("SteamDealsNet", "$method $path -> NET ERR ${e.javaClass.simpleName}: ${e.message}")
                throw ApiException.Network(e)
            }
            response.use { resp ->
                val text = resp.body?.string().orEmpty()
                if (resp.code !in 200..299) {
                    android.util.Log.e("SteamDealsNet", "$method $path -> ${resp.code}: ${text.take(160)}")
                }
                when {
                    resp.code in 200..299 -> text
                    resp.code == 401 && parseError(text) == ApiException.ERROR_LOGIN_REQUIRED ->
                        throw ApiException.LoginRequired
                    resp.code == 403 && parseError(text) == ApiException.ERROR_MUST_CHANGE_PASSWORD ->
                        throw ApiException.MustChangePassword
                    else -> throw ApiException.Http(
                        code = resp.code,
                        error = parseError(text),
                        retryAfterSec = resp.header("retry-after")?.toIntOrNull(),
                    )
                }
            }
        }

    private fun parseError(text: String): String? = runCatching {
        (json.parseToJsonElement(text) as? JsonObject)
            ?.get("error")?.jsonPrimitive?.contentOrNull
    }.getOrNull()

    inline fun <reified T> parse(text: String): T = json.decodeFromString(text)

    /**
     * 内置更新下载：OkHttp 流式写文件，边下边算 SHA-256，onProgress(bytes, totalBytes)。
     * 返回 Pair(文件, sha256Hex)。
     */
    suspend fun download(
        url: String,
        dest: File,
        onProgress: (Long, Long) -> Unit = { _, _ -> },
    ): Pair<File, String> = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).build()
        val response = try {
            client.newCall(request).execute()
        } catch (e: Exception) {
            throw ApiException.Network(e)
        }
        response.use { resp ->
            if (!resp.isSuccessful) {
                throw ApiException.Http(resp.code, "下载失败（HTTP ${resp.code}）")
            }
            val body = resp.body ?: throw ApiException.Http(resp.code, "下载失败（空响应体）")
            dest.parentFile?.mkdirs()
            val digest = MessageDigest.getInstance("SHA-256")
            val total = body.contentLength()
            var done = 0L
            dest.outputStream().use { out ->
                body.byteStream().use { input ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        digest.update(buf, 0, n)
                        done += n
                        onProgress(done, total)
                    }
                }
            }
            dest to digest.digest().joinToString("") { "%02x".format(it) }
        }
    }
}
