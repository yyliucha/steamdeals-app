package cn.yyliucha.steamdeals.data.api

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * CM-1（docs/APP-PARITY.md §3）：服务端 developers/publishers 历史数据形态混杂，统一解析成名称列表：
 * - 纯字符串数组 `["Re-Logic"]`（老数据）→ 原样；
 * - JSON 对象文本元素 `["{\"name\":\"Ubisoft Mainz\"}"]`（生产实测形态）→ 解析取 name；
 * - 对象数组 `[{"name":…,"creator_clan_account_id":…}]`（契约形态）→ 取 name；
 * - 单对象/单字符串兜底；空白元素过滤。
 */
object NameListSerializer : KSerializer<List<String>> {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("cn.yyliucha.steamdeals.NameList", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): List<String> {
        val el = (decoder as? JsonDecoder)?.decodeJsonElement() ?: return emptyList()
        return namesFrom(el)
    }

    override fun serialize(encoder: Encoder, value: List<String>) {
        (encoder as? JsonEncoder)?.encodeJsonElement(JsonArray(value.map { JsonPrimitive(it) }))
    }

    /** 纯函数：可从单测直接驱动（生产真实报文夹具）。 */
    fun namesFrom(el: JsonElement): List<String> = when (el) {
        is JsonArray -> el.mapNotNull { nameOf(it) }
        else -> listOfNotNull(nameOf(el))
    }

    /** 单个元素 → 名称：对象取 name；JSON 对象文本解析取 name；普通字符串原样；其余丢弃。 */
    fun nameOf(el: JsonElement): String? = when (el) {
        is JsonPrimitive -> {
            if (!el.isString) null
            else {
                val s = el.content
                val t = s.trim()
                when {
                    t.isEmpty() -> null
                    t.startsWith("{") -> runCatching {
                        (json.parseToJsonElement(t) as? JsonObject)
                            ?.get("name")?.jsonPrimitive?.contentOrNull
                    }.getOrNull()?.takeIf { it.isNotBlank() }
                    else -> s
                }
            }
        }
        is JsonObject -> el["name"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
        else -> null
    }
}

// ---------------- 认证 ----------------

@Serializable
data class TokenResponse(
    val token: String,
    val expiresAt: String? = null,
    val user: UserDto,
)

@Serializable
data class UserDto(
    val id: Long,
    val username: String,
    val role: String = "user",
    val mustChangePassword: Boolean = false,
    // CX 批（docs/USER-PROFILE-EDIT.md）：本人资料字段（Web /api/me 同口径，App /api/app/me 透传）
    val email: String? = null,
    val emailVerified: Boolean = false,
    val displayName: String? = null,
    val bio: String? = null,
    val avatarSource: String? = null,
    val avatarMime: String? = null,
    val avatarUpdatedAt: String? = null,
    val createdAt: String? = null,
)

@Serializable
data class CaptchaResponse(
    val token: String,
    val svg: String,
    val expiresInSec: Int = 300,
)

@Serializable
data class OkResponse(
    val ok: Boolean = true,
    val message: String? = null,
)

/** CZ 批（docs/EMAIL-CHANGE.md）：换邮箱发码响应 { ok, hint }——已占用邮箱也回同样文案（不泄露）。 */
@Serializable
data class EmailChangeRequestResponse(
    val ok: Boolean = false,
    val hint: String? = null,
)

/** CZ 批：换邮箱验证响应 { ok, message }；成功 message 提示「用新邮箱重新登录」。 */
@Serializable
data class EmailChangeVerifyResponse(
    val ok: Boolean = false,
    val message: String? = null,
)

// ---------------- App 命名空间 ----------------

@Serializable
data class StatusResponse(
    val banned: Boolean = false,
    val type: String? = null,
)

@Serializable
data class LatestResponse(
    val version: String? = null,
    val versionCode: Int? = null,
    val minVersionCode: Int? = null,
    val notes: List<String>? = null,
    val apkPath: String? = null,
    val sha256: String? = null,
    val size: Long? = null,
    val releasedAt: String? = null,
)

@Serializable
data class SiteResponse(
    val name: String = "SteamDeals",
    val version: String? = null,
    val appealEmail: String? = null,
    val adultDisplay: String = "hide",
    val registration: RegistrationInfo = RegistrationInfo(),
)

@Serializable
data class RegistrationInfo(
    val enabled: Boolean = true,
    val smtpReady: Boolean = false,
)

// ---------------- 游戏 ----------------

@Serializable
data class OwnedByDto(
    val steamId: String? = null,
    val name: String? = null,
)

@Serializable
data class OfferDto(
    // regionOffers（详情报价）字段
    val region: String? = null,
    val currency: String? = null,
    val platform_code: String? = null,
    val platform_name: String? = null,
    val price_cents: Long? = null,
    val original_cents: Long? = null,
    val discount_pct: Int? = null,
    val buy_url: String? = null,
    val last_seen_at: String? = null,
    // all_offers（game 内嵌）字段
    val platform: String? = null,
    val price: Long? = null,
)

@Serializable
data class GameDto(
    val id: Long,
    val steam_appid: Long? = null,
    val name: String,
    val name_en: String? = null,
    val app_type: String? = null,
    val heat: Double? = null,
    val release_date: String? = null,
    val interested: Boolean? = null,
    val user_interested: Boolean? = null,
    val user_owned: Boolean? = null,
    val owned: Boolean = false,
    val owned_by: List<OwnedByDto> = emptyList(),
    val wishlisted: Boolean = false,
    val wishlisted_by: List<OwnedByDto> = emptyList(),
    val site_wishlisted: Boolean = false,
    val blocked: Boolean = false,
    val description: String? = null,
    @Serializable(with = NameListSerializer::class)
    val developers: List<String> = emptyList(),
    @Serializable(with = NameListSerializer::class)
    val publishers: List<String> = emptyList(),
    val genres: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val steam_status: String? = null,
    val required_age: Int? = null,
    val adult_flag: Boolean = false,
    val mature_flag: Boolean = false,
    val current_low: Long? = null,
    val currency: String? = null,
    val steam_price: Long? = null,
    val current_original: Long? = null,
    val discount: Int? = null,
    val hist_low: Long? = null,
    val prev_hist_low: Long? = null,
    val day_count: Int? = null,
    val last_seen_at: String? = null,
    val low_platform_name: String? = null,
    val low_platform: String? = null,
    val all_offers: List<OfferDto> = emptyList(),
)

@Serializable
data class GamesResponse(
    val games: List<GameDto> = emptyList(),
    val total: Int = 0,
    val page: Int = 1,
    val pageSize: Int = 20,
    val pages: Int = 1,
    val sort: String = "discount",
    val owned: String = "exclude",
    val interest: String = "all",
    val q: String = "",
    val type: String = "all",
)

@Serializable
data class SeriesPoint(
    val day: String,
    val price_cents: Long? = null,
    val currency: String? = null,
)

@Serializable
data class DlcSummaryDto(
    val total: Int = 0,
    val synced: Boolean = false,
    val filled: Int = 0,
)

@Serializable
data class GameDetailResponse(
    val game: GameDto,
    val offers: List<OfferDto> = emptyList(),
    val series: List<SeriesPoint> = emptyList(),
    val dlc: DlcSummaryDto = DlcSummaryDto(),
)

@Serializable
data class DlcItemDto(
    val appid: Long,
    val name: String,
    val price_cents: Long? = null,
    val original_cents: Long? = null,
    val discount_pct: Int? = null,
    val currency: String? = null,
    val in_stock: Boolean = true,
    val buy_url: String? = null,
)

@Serializable
data class DlcListResponse(
    val total: Int = 0,
    val page: Int = 1,
    val pageSize: Int = 10,
    val synced: Boolean = false,
    val items: List<DlcItemDto> = emptyList(),
)

@Serializable
data class CountResponse(
    val priced: Int = 0,
    val catalog: Int = 0,
)

// ---------------- 用户 ----------------

@Serializable
data class PrefsDto(
    val hide_uninterested: Boolean = false,
    val only_discount: Boolean = false,
    val sort: String? = "discount",
    val tab: String? = "all",
    val theme: String? = null,
    // CX 批：到价提醒邮件开关（true=发邮件）
    val email_alerts: Boolean = true,
)

@Serializable
data class MeResponse(
    val user: UserDto? = null,
    val prefs: PrefsDto? = null,
    val timezone: String? = null,
    // DA 批（站点 docs/APP-FILTER-SERVER-SIDE.md §3）：首页「已拥有」chip 仅登录且已绑定 Steam 时显示
    val steamBound: Boolean = false,
)

@Serializable
data class PrefsSaveResponse(
    val prefs: PrefsDto? = null,
)

// ---------------- 个人资料（CX，docs/USER-PROFILE-EDIT.md） ----------------

@Serializable
data class ProfileSaveResponse(
    val user: UserDto? = null,
    val prefs: PrefsDto? = null,
)

@Serializable
data class AvatarUploadResponse(
    val avatarSource: String? = null,
    val avatarMime: String? = null,
    val avatarUpdatedAt: String? = null,
)

/** DQ（docs/PROFILE-UI-2026-11-19.md §3）：设为默认只写 default_steam_id——响应不再联动头像。 */
@Serializable
data class SteamDefaultResponse(
    val ok: Boolean = false,
    val defaultSteamId: String? = null,
)

@Serializable
data class SteamAccountDto(
    val steamId: String,
    val name: String? = null,
    val avatar: String? = null,
    val owned: Int = 0,
    val wishlist: Int = 0,
    val lastSyncedAt: String? = null,
    val boundAt: String? = null,
    val isDefault: Boolean = false,
)

@Serializable
data class LibraryStatsDto(
    val ownedTotal: Int = 0,
    val wishlistTotal: Int = 0,
    val siteWishlist: Int = 0,
    val alerts: Int = 0,
    val histLowHits: Int = 0,
)

@Serializable
data class MeLibraryResponse(
    val accounts: List<SteamAccountDto> = emptyList(),
    val stats: LibraryStatsDto = LibraryStatsDto(),
)

@Serializable
data class LibraryItemDto(
    val appid: Long = 0,
    val name: String? = null,
    val name_en: String? = null,
    val playtime_minutes: Int? = null,
    val last_played_at: String? = null,
    val synced_at: String? = null,
    val in_db: Boolean = false,
    val current_low: Long? = null,
    val hist_low: Long? = null,
)

/** 库 / 愿望单条目响应：library 端点回 games、wishlist 端点回 items（Web 契约形状差异，App 统一取 list）。 */
@Serializable
data class LibraryItemsResponse(
    val games: List<LibraryItemDto>? = null,
    val items: List<LibraryItemDto>? = null,
    val total: Int = 0,
    val page: Int = 1,
    val pageSize: Int = 10,
) {
    val list: List<LibraryItemDto> get() = games ?: items ?: emptyList()
}

// ---------------- 公告 ----------------

@Serializable
data class AnnouncementDto(
    val id: Long,
    val title: String,
    val body: String? = null,
    val level: String? = null,
    val startsAt: String? = null,
    val endsAt: String? = null,
)

@Serializable
data class AnnouncementsResponse(
    val announcements: List<AnnouncementDto> = emptyList(),
    val readIds: List<Long> = emptyList(),
)

@Serializable
data class ReadResponse(
    val ok: Boolean = true,
    val readIds: List<Long> = emptyList(),
)

// ---------------- 注册 ----------------

@Serializable
data class RegisterRequestResponse(
    val ok: Boolean = true,
    val hint: String? = null,
)

@Serializable
data class RegisterVerifyResponse(
    val user: UserDto? = null,
)
