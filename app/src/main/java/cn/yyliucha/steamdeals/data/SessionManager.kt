package cn.yyliucha.steamdeals.data

import cn.yyliucha.steamdeals.data.api.ApiException
import cn.yyliucha.steamdeals.data.api.SteamDealsApi
import cn.yyliucha.steamdeals.data.api.TokenResponse
import cn.yyliucha.steamdeals.data.api.UserDto
import cn.yyliucha.steamdeals.data.store.SessionData
import cn.yyliucha.steamdeals.data.store.SessionStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 会话管理：Token 持久化 / 登录 / 退出（撤销远端 token）。 */
class SessionManager(
    private val store: SessionStore,
    private val api: SteamDealsApi,
    scope: CoroutineScope,
) {
    val session: StateFlow<SessionData> = store.data.stateIn(
        scope = scope,
        started = kotlinx.coroutines.flow.SharingStarted.Eagerly,
        initialValue = SessionData(),
    )

    /** 登录成功：写 token + user（明文 token 只存本机 DataStore）。 */
    suspend fun onTokenLogin(response: TokenResponse) {
        store.setToken(response.token, parseExpiresAt(response.expiresAt))
        store.setUser(response.user)
    }

    /** 退出登录：本地清空 + 尽力撤销远端 token（失败不阻塞）。 */
    suspend fun logout() {
        try {
            api.revokeToken()
        } catch (_: Exception) {
            // token 可能已过期/网络失败——本地照常清空
        }
        store.setUser(null)
        store.setToken(null)
        store.setIgnoredVersionCode(0)
    }

    /** 刷新用户信息（/api/me）。 */
    suspend fun refreshUser(): UserDto? = try {
        val me = api.me()
        if (me.user != null) {
            store.setUser(me.user)
            me.user
        } else {
            null
        }
    } catch (e: ApiException) {
        if (e is ApiException.LoginRequired) {
            store.setUser(null)
            store.setToken(null)
        }
        null
    }

    private fun parseExpiresAt(expiresAt: String?): Long =
        expiresAt?.let { runCatching { java.time.Instant.parse(it).toEpochMilli() }.getOrNull() } ?: 0
}

/** 登录/退出等会话级事件（一次性的，UI 只取一次）。 */
class SessionEvents {
    private val _loggedOut = MutableStateFlow(0L)
    val loggedOut: StateFlow<Long> = _loggedOut
    fun onLoggedOut() {
        _loggedOut.value += 1
    }
}
