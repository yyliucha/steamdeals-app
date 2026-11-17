package cn.yyliucha.steamdeals.di

import android.content.Context
import cn.yyliucha.steamdeals.BuildConfig
import cn.yyliucha.steamdeals.data.SessionManager
import cn.yyliucha.steamdeals.data.api.ApiClient
import cn.yyliucha.steamdeals.data.api.SteamDealsApi
import cn.yyliucha.steamdeals.data.store.SessionStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 手写 ServiceLocator（APP-NATIVE.md §4.1：不引 Hilt/Koin）。
 */
class ServiceLocator(context: Context) {

    private val appContext = context.applicationContext
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val sessionStore = SessionStore(appContext)

    init {
        // token 快照跟随 DataStore（OkHttp 拦截器是同步读取，避免冷启动丢 Bearer）
        appScope.launch {
            sessionStore.data.collect { sessionStore.updateTokenSnapshot(it.token) }
        }
    }

    // token/deviceId 由 SessionStore 的 Flow 快照提供；deviceId 惰性生成后由 Store 维护
    private var cachedDeviceId: String? = null

    val apiClient: ApiClient = ApiClient(
        baseUrl = BuildConfig.API_BASE_URL,
        tokenProvider = { sessionStore.snapshotToken },
        deviceIdProvider = { cachedDeviceId },
    )

    val api = SteamDealsApi(apiClient)
    val sessionManager = SessionManager(sessionStore, api, appScope)

    /** 安装时生成并持久化设备 ID（DataStore），随后所有请求带 X-Device-Id。 */
    suspend fun ensureDeviceId(): String {
        val id = sessionStore.ensureDeviceId()
        cachedDeviceId = id
        return id
    }

    companion object {
        @Volatile
        private var instance: ServiceLocator? = null

        fun get(context: Context): ServiceLocator =
            instance ?: synchronized(this) {
                instance ?: ServiceLocator(context).also { instance = it }
            }
    }
}
