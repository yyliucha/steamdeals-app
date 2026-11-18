package cn.yyliucha.steamdeals.data.api

import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * CX 批（docs/USER-PROFILE-EDIT.md）：个人资料请求构造的纯函数落点（JVM 单测直接锁定）。
 * SteamDealsApi 与 ProfileEditScreen 都从这里取路径/参数，保证「账号操作请求」与实现一致。
 */
object ProfileRequests {

    const val PATH_PROFILE = "/api/app/me/profile"
    const val PATH_AVATAR = "/api/app/me/avatar"
    const val PATH_LIBRARY = "/api/app/me/library"

    /** Steam 账号路径：/api/app/steam/accounts/<id>[/<action>]，steamId 做 URL 编码。 */
    fun steamAccountPath(steamId: String, action: String? = null): String {
        val enc = java.net.URLEncoder.encode(steamId, "UTF-8")
        return "/api/app/steam/accounts/$enc" + (action?.let { "/$it" } ?: "")
    }

    /** 库 / 愿望单条目查询参数（q 原样传，服务端做 trim/长度截断）。 */
    fun steamItemsQuery(q: String, page: Int, pageSize: Int): Map<String, String> =
        mapOf("q" to q, "page" to page.toString(), "pageSize" to pageSize.toString())

    /**
     * 保存资料的请求体：displayName/bio/theme 恒发（null=清空/跟随系统），emailAlerts 只在非空时发。
     * 与服务端 PATCH /api/me/profile 字段名一致（displayName/bio/theme/emailAlerts）。
     */
    fun profileBody(displayName: String?, bio: String?, theme: String?, emailAlerts: Boolean?): String =
        buildJsonObject {
            if (displayName == null) put("displayName", JsonNull) else put("displayName", displayName)
            if (bio == null) put("bio", JsonNull) else put("bio", bio)
            if (theme == null) put("theme", JsonNull) else put("theme", theme)
            emailAlerts?.let { put("emailAlerts", JsonPrimitive(it)) }
        }.toString()

    /**
     * 头像展示地址：无头像来源 → null（UI 回退首字）；否则 /api/avatar/:userId（custom 直接出图，
     * steam:<id> 由服务端 302 到 Steam CDN）。带 avatarUpdatedAt 作缓存破坏参数。
     */
    fun avatarUrl(baseUrl: String, userId: Long, avatarSource: String?, avatarUpdatedAt: String?): String? {
        if (avatarSource.isNullOrBlank()) return null
        val base = baseUrl.trimEnd('/')
        val v = avatarUpdatedAt?.takeIf { it.isNotBlank() } ?: "0"
        return "$base/api/avatar/$userId?v=$v"
    }
}
