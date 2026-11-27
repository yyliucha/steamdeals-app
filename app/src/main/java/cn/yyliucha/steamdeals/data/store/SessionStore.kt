package cn.yyliucha.steamdeals.data.store

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import cn.yyliucha.steamdeals.data.api.UserDto
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import java.util.UUID

private val Context.sessionDataStore by preferencesDataStore(name = "steamdeals_session")

data class SessionData(
    val token: String? = null,
    val tokenExpiresAt: Long = 0,
    val user: UserDto? = null,
    val deviceId: String = "",
    /** theme: system / light / dark（dark 与 web prefs.theme 对齐） */
    val theme: String = "system",
    /** 游客本地已读公告 id */
    val readAnnouncementIds: List<Long> = emptyList(),
    val searchHistory: List<String> = emptyList(),
    val lastUpdateCheckAt: Long = 0,
    val ignoredVersionCode: Int = 0,
    /** FI（站点 docs/ADMIN-RATING-FILTER.md）：18R 筛选开关（默认关闭；本地 DataStore 记忆；只控制筛选选中态，
     *  入口显隐由站点策略 adultDisplay 决定）。 */
    val adultEnabled: Boolean = false,
    val adultConfirmed: Boolean = false,
) {
    val isLoggedIn: Boolean get() = !token.isNullOrBlank()
}

/**
 * 本地持久化（DataStore）：
 * - Token（明文只存本机，库中只有哈希）与过期时间；
 * - 设备 ID：安装时生成一次 UUID，随 X-Device-Id 发送（APP-NATIVE.md §3.4）；
 * - 主题、游客已读公告、搜索历史、更新检查节流/忽略版本。
 * pendingAction 按要求只留内存（App 被杀即清空，不落盘）。
 */
class SessionStore(private val context: Context) {

    /** token 快照：网络层拦截器同步读取（DataStore Flow 是异步的，这里维护一个易失镜像）。 */
    @Volatile
    var snapshotToken: String? = null
        private set

    /** 由 ServiceLocator 的收集器调用，让快照跟随 DataStore。 */
    fun updateTokenSnapshot(token: String?) {
        snapshotToken = token
    }

    private object Keys {
        val TOKEN = stringPreferencesKey("token")
        val TOKEN_EXPIRES_AT = longPreferencesKey("token_expires_at")
        val USER_JSON = stringPreferencesKey("user_json")
        val DEVICE_ID = stringPreferencesKey("device_id")
        val THEME = stringPreferencesKey("theme")
        val READ_IDS = stringPreferencesKey("read_announcement_ids")
        val SEARCH_HISTORY = stringPreferencesKey("search_history")
        val LAST_UPDATE_CHECK_AT = longPreferencesKey("last_update_check_at")
        val IGNORED_VERSION_CODE = stringPreferencesKey("ignored_version_code")
        // FI（站点 docs/ADMIN-RATING-FILTER.md）：18R 筛选键——开关与年龄确认（本地记忆，入口显隐随站点策略）
        val ADULT_ENABLED = booleanPreferencesKey("adult_enabled")
        val ADULT_CONFIRMED = booleanPreferencesKey("adult_confirmed")
    }

    private val json = Json { ignoreUnknownKeys = true }

    val data: Flow<SessionData> = context.sessionDataStore.data.map { p ->
        SessionData(
            token = p[Keys.TOKEN],
            tokenExpiresAt = p[Keys.TOKEN_EXPIRES_AT] ?: 0,
            user = p[Keys.USER_JSON]?.let { runCatching { json.decodeFromString<UserDto>(it) }.getOrNull() },
            deviceId = p[Keys.DEVICE_ID].orEmpty(),
            theme = p[Keys.THEME] ?: "system",
            readAnnouncementIds = (p[Keys.READ_IDS] ?: "")
                .split(',')
                .mapNotNull { it.trim().toLongOrNull() },
            searchHistory = (p[Keys.SEARCH_HISTORY] ?: "")
                .split('\n')
                .filter { it.isNotBlank() },
            lastUpdateCheckAt = p[Keys.LAST_UPDATE_CHECK_AT] ?: 0,
            ignoredVersionCode = (p[Keys.IGNORED_VERSION_CODE] ?: "0").toIntOrNull() ?: 0,
            adultEnabled = p[Keys.ADULT_ENABLED] ?: false,
            adultConfirmed = p[Keys.ADULT_CONFIRMED] ?: false,
        )
    }

    /** 设备 ID：安装时生成一次并持久化（不使用 Android ID，隐私/重置问题）。 */
    suspend fun ensureDeviceId(): String {
        var id = data.firstOrNull()?.deviceId.orEmpty()
        if (id.isBlank()) {
            id = UUID.randomUUID().toString()
            context.sessionDataStore.edit { it[Keys.DEVICE_ID] = id }
        }
        return id
    }

    suspend fun setToken(token: String?, expiresAt: Long = 0) {
        snapshotToken = token
        context.sessionDataStore.edit {
            if (token == null) {
                it.remove(Keys.TOKEN)
                it.remove(Keys.TOKEN_EXPIRES_AT)
            } else {
                it[Keys.TOKEN] = token
                it[Keys.TOKEN_EXPIRES_AT] = expiresAt
            }
        }
    }

    suspend fun setUser(user: UserDto?) {
        context.sessionDataStore.edit {
            if (user == null) it.remove(Keys.USER_JSON)
            else it[Keys.USER_JSON] = json.encodeToString(UserDto.serializer(), user)
        }
    }

    /** GD：主题写盘——异常不再静默（更不会崩到 main），记 logcat 并返回是否写入成功；
     *  ViewModel 侧保留内存态兜底，写失败时本次会话仍按用户选择显示。 */
    suspend fun setTheme(theme: String): Boolean = runCatching {
        context.sessionDataStore.edit { it[Keys.THEME] = theme }
    }.onFailure {
        android.util.Log.w("SteamDeals", "setTheme failed: $it")
    }.isSuccess

    suspend fun setReadAnnouncementIds(ids: List<Long>) {
        context.sessionDataStore.edit { it[Keys.READ_IDS] = ids.joinToString(",") }
    }

    suspend fun addSearchKeyword(keyword: String) {
        val kw = keyword.trim()
        if (kw.isBlank()) return
        context.sessionDataStore.edit { p ->
            val current = (p[Keys.SEARCH_HISTORY] ?: "").split('\n').filter { it.isNotBlank() }
            val next = (listOf(kw) + current.filter { it != kw }).take(20)
            p[Keys.SEARCH_HISTORY] = next.joinToString("\n")
        }
    }

    suspend fun clearSearchHistory() {
        context.sessionDataStore.edit { it.remove(Keys.SEARCH_HISTORY) }
    }

    suspend fun setLastUpdateCheckAt(at: Long) {
        context.sessionDataStore.edit { it[Keys.LAST_UPDATE_CHECK_AT] = at }
    }

    suspend fun setIgnoredVersionCode(code: Int) {
        context.sessionDataStore.edit { it[Keys.IGNORED_VERSION_CODE] = code.toString() }
    }

    /** FI（站点 docs/ADMIN-RATING-FILTER.md）：18R 筛选开关（默认关闭；开=只看 18R / 关=恢复全部）。 */
    suspend fun setAdultEnabled(enabled: Boolean) {
        context.sessionDataStore.edit { it[Keys.ADULT_ENABLED] = enabled }
    }

    /** FI：首次开启 18R 筛选的年龄确认（确认过不再弹）。 */
    suspend fun setAdultConfirmed() {
        context.sessionDataStore.edit { it[Keys.ADULT_CONFIRMED] = true }
    }
}
