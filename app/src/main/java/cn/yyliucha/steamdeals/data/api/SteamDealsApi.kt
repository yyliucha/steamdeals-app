package cn.yyliucha.steamdeals.data.api

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * 接口一律按 docs/APP-API.md（站点仓库 APP-NATIVE.md §3 的 API 契约）。
 * 路径全部走 /api/app 命名空间（服务端去前缀路由；/api/app/latest、/api/app/status 为精确例外）。
 */
class SteamDealsApi(private val client: ApiClient) {

    // ---------- App 命名空间 ----------

    suspend fun appStatus(): StatusResponse =
        client.parse(client.get("/api/app/status"))

    suspend fun latest(): LatestResponse =
        client.parse(client.get("/api/app/latest"))

    suspend fun site(): SiteResponse =
        client.parse(client.get("/api/app/site"))

    // ---------- 认证 ----------

    suspend fun captcha(): CaptchaResponse =
        client.parse(client.get("/api/app/captcha"))

    suspend fun login(
        username: String,
        password: String,
        captchaToken: String,
        captchaAnswer: String,
        rememberMe: Boolean,
        deviceName: String? = null,
    ): TokenResponse = client.parse(
        client.post(
            "/api/app/auth/token",
            jsonBody {
                put("username", username)
                put("password", password)
                put("captchaToken", captchaToken)
                put("captchaAnswer", captchaAnswer)
                put("rememberMe", rememberMe)
                deviceName?.let { put("deviceName", it) }
            },
        ),
    )

    suspend fun revokeToken(): OkResponse = client.parse(client.post("/api/app/auth/token/revoke"))

    // ---------- 注册（邮箱验证码，web 契约 /api/auth/register 系列） ----------

    suspend fun registerRequest(
        username: String,
        email: String,
        captchaToken: String,
        captchaAnswer: String,
    ): RegisterRequestResponse = client.parse(
        client.post(
            "/api/app/auth/register/request",
            jsonBody {
                put("username", username)
                put("email", email)
                put("captchaToken", captchaToken)
                put("captchaAnswer", captchaAnswer)
            },
        ),
    )

    suspend fun registerVerify(
        email: String,
        code: String,
        username: String,
        password: String,
    ): RegisterVerifyResponse = client.parse(
        client.post(
            "/api/app/auth/register/verify",
            jsonBody {
                put("email", email)
                put("code", code)
                put("username", username)
                put("password", password)
            },
        ),
    )

    // ---------- 用户 ----------

    suspend fun me(): MeResponse = client.parse(client.get("/api/app/me"))

    suspend fun savePrefs(prefs: PrefsDto): PrefsSaveResponse = client.parse(
        client.post(
            "/api/app/prefs",
            jsonBody {
                prefs.sort?.let { put("sort", it) }
                prefs.tab?.let { put("tab", it) }
                prefs.theme?.let { put("theme", it) }
            },
        ),
    )

    suspend fun changePassword(oldPassword: String, newPassword: String): OkResponse = client.parse(
        client.post(
            "/api/app/user/password",
            jsonBody {
                put("oldPassword", oldPassword)
                put("newPassword", newPassword)
            },
        ),
    )

    // ---------- 游戏列表（公开） ----------

    suspend fun games(params: Map<String, String>): GamesResponse {
        val text = client.get("/api/app/games", params)
        return client.parse(text)
    }

    /** POST /api/app/games/search：登录用户愿望单列表用（wishlist=true 只在该入口生效，web 契约）。 */
    suspend fun gamesSearch(body: String): GamesResponse =
        client.parse(client.post("/api/app/games/search", body))

    suspend fun gamesCount(): CountResponse = client.parse(client.get("/api/app/games/count"))

    suspend fun gameDetail(appid: Long): GameDetailResponse =
        client.parse(client.get("/api/app/games/$appid", mapOf("region" to "cn")))

    suspend fun dlcList(appid: Long, page: Int = 1, pageSize: Int = 10): DlcListResponse =
        client.parse(
            client.get(
                "/api/app/games/$appid/dlc",
                mapOf("page" to page.toString(), "pageSize" to pageSize.toString()),
            ),
        )

    // ---------- 用户标记 / 愿望单（需 Bearer） ----------

    /** 感兴趣 / 不感兴趣（docs/APP-API.md §4：普通用户可操作自己的标记）。 */
    suspend fun setInterest(appid: Long, interested: Boolean): Json = client.parse(
        client.post(
            "/api/app/games/$appid/interest",
            jsonBody { put("interested", interested) },
        ),
    )

    suspend fun addWishlist(appid: Long): Json = client.parse(
        client.post("/api/app/wishlist", jsonBody { put("appid", appid) }),
    )

    suspend fun removeWishlist(appid: Long): Json =
        client.parse(client.delete("/api/app/wishlist/$appid"))

    suspend fun removeWishlistById(gameId: Long): Json =
        client.parse(client.delete("/api/app/wishlist/id/$gameId"))

    // ---------- 公告 ----------

    suspend fun announcements(): AnnouncementsResponse =
        client.parse(client.get("/api/app/announcements"))

    suspend fun markAnnouncementsRead(ids: List<Long>): ReadResponse = client.parse(
        client.post(
            "/api/app/announcements/read",
            jsonBody { put("ids", JsonArray(ids.map { JsonPrimitive(it) })) },
        ),
    )

    private inline fun jsonBody(block: kotlinx.serialization.json.JsonObjectBuilder.() -> Unit): String =
        buildJsonObject(block).toString()
}
