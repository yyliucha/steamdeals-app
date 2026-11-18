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

    // ---------- 个人资料（CX，docs/USER-PROFILE-EDIT.md；与 Web 同一套接口） ----------

    /** 保存昵称/简介/主题/邮件开关（theme：'light'/'dark'/null=跟随系统；只改自己的资料，服务端忽略其余字段）。 */
    suspend fun meProfile(
        displayName: String?,
        bio: String?,
        theme: String?,
        emailAlerts: Boolean?,
    ): ProfileSaveResponse = client.parse(
        client.patch(ProfileRequests.PATH_PROFILE, ProfileRequests.profileBody(displayName, bio, theme, emailAlerts)),
    )

    /** 上传自定义头像：客户端裁剪 256×256 后的 dataUrl（≤512KB，JPG/PNG/WebP/GIF）。 */
    suspend fun meAvatar(dataUrl: String): AvatarUploadResponse = client.parse(
        client.post(ProfileRequests.PATH_AVATAR, jsonBody { put("dataUrl", dataUrl) }),
    )

    suspend fun meAvatarDelete(): AvatarUploadResponse = client.parse(client.delete(ProfileRequests.PATH_AVATAR))

    /** 我的游戏库与统计（每账号一卡 + 5 项汇总）。 */
    suspend fun meLibrary(): MeLibraryResponse = client.parse(client.get(ProfileRequests.PATH_LIBRARY))

    /** 设为默认 Steam 账号（头像默认取该账号）。 */
    suspend fun steamAccountDefault(steamId: String): AvatarUploadResponse = client.parse(
        client.post(ProfileRequests.steamAccountPath(steamId, "default")),
    )

    /** 解绑（保留历史「曾拥有」）。 */
    suspend fun steamAccountUnbind(steamId: String): OkResponse = client.parse(
        client.delete(ProfileRequests.steamAccountPath(steamId)),
    )

    /** 账号库 / 愿望单条目（kind = library | wishlist；搜索 + 分页）。 */
    suspend fun steamAccountItems(
        steamId: String,
        kind: String,
        q: String,
        page: Int,
        pageSize: Int,
    ): LibraryItemsResponse = client.parse(
        client.get(
            ProfileRequests.steamAccountPath(steamId, kind),
            ProfileRequests.steamItemsQuery(q, page, pageSize),
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

    /**
     * 感兴趣 / 不感兴趣（docs/APP-API.md §4：普通用户可操作自己的标记）。
     * 契约端点是 /api/games/:appid/interest；在 CA-1 把该路由放开为「登录用户可操作」之前，
     * 服务端仍把它当作管理员专用 → 403 时回退现有用户级 /flags 端点（同 web 契约，请求体相同）。
     * 契约端点放开后首请求即成功，回退路径自然失效。
     * 返回响应原文（调用方只关心 2xx，不解析具体形状）。
     */
    suspend fun setInterest(appid: Long, interested: Boolean): String = try {
        client.post(
            "/api/app/games/$appid/interest",
            jsonBody { put("interested", interested) },
        )
    } catch (e: ApiException) {
        if (e is ApiException.Http && e.code == 403 && e.error != ApiException.ERROR_MUST_CHANGE_PASSWORD) {
            client.post(
                "/api/app/games/$appid/flags",
                jsonBody { put("interested", interested) },
            )
        } else {
            throw e
        }
    }

    suspend fun addWishlist(appid: Long): String =
        client.post("/api/app/wishlist", jsonBody { put("appid", appid) })

    suspend fun removeWishlist(appid: Long): String =
        client.delete("/api/app/wishlist/$appid")

    suspend fun removeWishlistById(gameId: Long): String =
        client.delete("/api/app/wishlist/id/$gameId")

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
