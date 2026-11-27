package cn.yyliucha.steamdeals.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import cn.yyliucha.steamdeals.App
import cn.yyliucha.steamdeals.BuildConfig
import cn.yyliucha.steamdeals.R
import cn.yyliucha.steamdeals.data.PendingAction
import cn.yyliucha.steamdeals.data.PendingActionHolder
import cn.yyliucha.steamdeals.data.api.ApiClient
import cn.yyliucha.steamdeals.data.api.ApiException
import cn.yyliucha.steamdeals.data.api.PrefsDto
import cn.yyliucha.steamdeals.data.store.SessionData
import cn.yyliucha.steamdeals.di.ServiceLocator
import cn.yyliucha.steamdeals.ui.home.HomeScreenState
import cn.yyliucha.steamdeals.ui.profile.ProfileEditState
import cn.yyliucha.steamdeals.ui.search.SearchScreenState
import cn.yyliucha.steamdeals.ui.theme.ThemeModeState
import cn.yyliucha.steamdeals.ui.update.UpdateManager
import cn.yyliucha.steamdeals.ui.wishlist.WishlistScreenState
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 启动封禁检查状态（docs/APP-NATIVE.md §3.4）。 */
sealed interface BanState {
    data object Checking : BanState
    data object Clear : BanState
    data class Banned(val type: String?) : BanState
}

/** 内置更新 UI 状态。 */
sealed interface UpdateUiState {
    data object Idle : UpdateUiState
    data object Checking : UpdateUiState
    data class Available(val info: UpdateManager.UpdateInfo, val force: Boolean) : UpdateUiState
    data class AskNetwork(val info: UpdateManager.UpdateInfo) : UpdateUiState
    data class Downloading(val progress: Float) : UpdateUiState // -1 = 未知总量
    data class VerifyFailed(val message: String) : UpdateUiState
    data class DownloadFailed(val message: String) : UpdateUiState
    data class Ready(val info: UpdateManager.UpdateInfo) : UpdateUiState
    data object Latest : UpdateUiState
    data object CheckFailed : UpdateUiState
    /** DK（站点 TODO DK B）：有新版但下载地址暂不可用——提示到站点 /app 页下载，不算「检查失败」。 */
    data class Unavailable(val version: String) : UpdateUiState
}

class AppViewModel(application: Application) : AndroidViewModel(application) {

    private val locator: ServiceLocator = (application as App).locator
    private val store = locator.sessionStore
    private val api = locator.api

    /** 供各屏直接调接口（只读暴露）。 */
    val apiClient: ApiClient = locator.apiClient
    val storePublic: cn.yyliucha.steamdeals.data.store.SessionStore = store
    val apiPublic: cn.yyliucha.steamdeals.data.api.SteamDealsApi = api

    /** 登录成功落库（LoginScreen 用）。DC：随后把游客本地已读公告合并到服务端并刷新口径。
     *  DQ：token 响应不含 displayName 等资料字段，登录后补拉 /api/me 刷新会话用户，
     *  「我的」页头像才能按昵称首字母/首个汉字回退（与资料页头像一致）。 */
    suspend fun onTokenLogin(response: cn.yyliucha.steamdeals.data.api.TokenResponse) {
        locator.sessionManager.onTokenLogin(response)
        locator.sessionManager.refreshUser()
        announcementsState.mergeLocalReadsToServer()
        announcementsState.refresh()
        // DY：账号切换 → 清掉上一账号的资料/愿望单缓存，避免加载完成前闪现旧账号数据
        profileState.resetForSessionChange()
        wishlistState.reset()
    }

    /** CX 批：资料保存后刷新本地会话用户（昵称/头像回显在「我的」页与资料页）。 */
    suspend fun refreshUser() {
        locator.sessionManager.refreshUser()
    }

    val session: StateFlow<SessionData> = locator.sessionManager.session

    /** 公告（DC/EG）：角标 / 弹层 / 已读的全局状态，首页与「我的」共用。
     *  EG：经 AnnouncementsGateway 缝注入 api——状态逻辑与网络解耦，单测可挂起拦截复现竞态。 */
    val announcementsState = cn.yyliucha.steamdeals.ui.notifications.AnnouncementsState(
        scope = viewModelScope,
        session = session,
        gateway = object : cn.yyliucha.steamdeals.ui.notifications.AnnouncementsGateway {
            override suspend fun fetch() = api.announcements()
            override suspend fun markRead(ids: List<Long>) {
                api.markAnnouncementsRead(ids)
            }
        },
        persistLocalReads = { ids -> store.setReadAnnouncementIds(ids) },
        localReadIds = { store.data.firstOrNull()?.readAnnouncementIds.orEmpty() },
    )

    // ---------- DY（站点 docs/ANDROID-POLISH.md §DY）：页面状态提升到 ViewModel ----------
    // 各列表/表单页离开组合（进详情、切 Tab、旋转）后返回时保持离开时的状态；
    // 滚动位置仍走 rememberSaveable（LazyListState.Saver / ScrollState.Saver，可跨进程保存），
    // 业务状态（搜索词/结果/筛选/表单）由这里统一持有。
    val searchState = SearchScreenState(this)
    val homeState = HomeScreenState(this, viewModelScope)
    val wishlistState = WishlistScreenState(this, viewModelScope)
    val profileState = ProfileEditState(this, viewModelScope)

    // GD（站点 TODO GD）：主题切换立即生效——内存态优先 + DataStore 写入异常兜底
    // （状态机与单测见 ThemeModeState；写盘失败只记日志，本次会话仍按用户选择显示）。
    private val themeModeState = ThemeModeState(
        scope = viewModelScope,
        persistedTheme = session.map { it.theme },
        writeTheme = { mode -> store.setTheme(mode) },
        onWriteError = { e -> android.util.Log.w("SteamDeals", "setTheme failed: $e") },
    )

    /** 主题（system / light / dark）：用户本次选择 > DataStore 持久化值。 */
    val themeMode: StateFlow<String> = themeModeState.themeMode

    // ---------- FI（站点 docs/ADMIN-RATING-FILTER.md）：18R 筛选按站点策略控制 ----------
    // 本地开关 adultEnabled/adultConfirmed（DataStore 记忆）只控制筛选选中态；
    // 18R chip 的显隐由 /api/site 的 adultDisplay 决定（hide=无入口、请求 adult=hide；
    // badge/show=显示入口，badge 卡片带 18+ 角标、show 无角标）。
    val adultEnabled: StateFlow<Boolean> = session.map { it.adultEnabled }.stateIn(
        viewModelScope, SharingStarted.Eagerly, false,
    )
    val adultConfirmed: StateFlow<Boolean> = session.map { it.adultConfirmed }.stateIn(
        viewModelScope, SharingStarted.Eagerly, false,
    )

    fun setAdultEnabled(enabled: Boolean) {
        viewModelScope.launch { store.setAdultEnabled(enabled) }
    }

    fun confirmAdult() {
        viewModelScope.launch { store.setAdultConfirmed() }
    }

    /** 站点内容展示策略（/api/site 的 adultDisplay）：hide / badge / show（网络失败保持 hide）。 */
    private val _siteAdultDisplay = MutableStateFlow("hide")
    val siteAdultDisplay: StateFlow<String> = _siteAdultDisplay

    /** 拉取站点策略（启动与进入首页/搜索时刷新；后台改参数后回页面即生效）。 */
    fun refreshSiteInfo() {
        viewModelScope.launch {
            runCatching { api.site() }.onSuccess { resp ->
                _siteAdultDisplay.value = resp.adultDisplay.ifBlank { "hide" }
            }
        }
    }

    // ---------- 启动封禁检查 ----------
    private val _banState = MutableStateFlow<BanState>(BanState.Checking)
    val banState: StateFlow<BanState> = _banState

    // ---------- EA-6（站点 docs/ANDROID-POLISH.md §EA-6）：全局弱网提示 ----------
    // 任何请求网络层失败 → 提示条亮起（不阻塞页面）；「重试」ping /api/app/status，
    // 任一请求成功即自动隐藏并 toast「网络已恢复」。
    private val _networkBanner = MutableStateFlow(false)
    val networkBanner: StateFlow<Boolean> = _networkBanner

    init {
        apiClient.networkStateListener = { ok -> onNetworkResult(ok) }
        checkBan()
        refreshSiteInfo()
    }

    private fun onNetworkResult(ok: Boolean) {
        if (ok) {
            if (_networkBanner.value) {
                _networkBanner.value = false
                val app = getApplication<Application>()
                android.widget.Toast.makeText(
                    app, app.getString(R.string.network_recovered), android.widget.Toast.LENGTH_SHORT,
                ).show()
            }
        } else {
            _networkBanner.value = true
        }
    }

    /** 弱网提示条「重试」：轻量 ping；成功由 networkStateListener 收尾隐藏，失败保持提示条。 */
    fun retryNetwork() {
        viewModelScope.launch {
            runCatching { api.appStatus() }
        }
    }

    private fun checkBan() {
        viewModelScope.launch {
            try {
                locator.ensureDeviceId()
                val status = api.appStatus()
                _banState.value = if (status.banned) BanState.Banned(status.type) else BanState.Clear
            } catch (_: Exception) {
                // 网络失败不阻塞进 App（无法确认封禁 ≠ 未封禁，但启动检查失败静默放行）
                _banState.value = BanState.Clear
            }
        }
    }

    // ---------- 登录门槛（游客点用户功能 → 弹提示） ----------
    private val _loginRequired = MutableSharedFlow<PendingAction?>(extraBufferCapacity = 4)
    val loginRequired: MutableSharedFlow<PendingAction?> = _loginRequired

    fun onLoginRequired(action: PendingAction?) {
        // 新的一次登录门槛操作覆盖旧的（同一时间只保留最近一次，§4.2.2）
        if (action != null) PendingActionHolder.set(action)
        // Token 失效也算未登录：清掉本地残留，避免重复弹
        viewModelScope.launch {
            if (session.value.token != null) {
                store.setToken(null)
                store.setUser(null)
            }
        }
        _loginRequired.tryEmit(action)
    }

    /** 登录成功后自动完成刚才的操作一次（§4.2.2）：执行即清空，成败都发结果。 */
    private val _pendingActionResult = MutableSharedFlow<Pair<PendingAction, Boolean>>(replay = 4, extraBufferCapacity = 4)
    val pendingActionResult = _pendingActionResult

    suspend fun executePendingAction() {
        val action = PendingActionHolder.take() ?: return
        val ok = try {
            when (action.type) {
                PendingAction.TYPE_INTEREST -> {
                    action.appid?.let { api.setInterest(it, true) }; true
                }
                PendingAction.TYPE_UNINTEREST -> {
                    action.appid?.let { api.setInterest(it, false) }; true
                }
                PendingAction.TYPE_WISHLIST -> {
                    action.appid?.let { api.addWishlist(it) }; true
                }
                else -> false
            }
        } catch (_: Exception) {
            false
        }
        _pendingActionResult.emit(action to ok)
    }

    /** 登录成功收尾：在 viewModelScope 执行 pendingAction（页面销毁也不取消）。 */
    fun onLoginSuccessFinished() {
        viewModelScope.launch {
            if (PendingActionHolder.current.value != null) {
                executePendingAction()
            }
        }
    }

    // ---------- 主题（登录用户同步 /api/prefs；游客仅本地跟随系统） ----------
    fun setTheme(mode: String) {
        // GD：先内存态 → 界面立即换色（不等 DataStore 往返，写盘崩/卡都不影响本次切换）；
        // 落盘与登录用户的服务端同步随后异步进行，失败只记日志。
        themeModeState.setTheme(mode)
        viewModelScope.launch {
            if (session.value.isLoggedIn) {
                runCatching {
                    api.savePrefs(PrefsDto(theme = if (mode == "system") null else mode))
                }
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            locator.sessionManager.logout()
            // DY：退出登录 → 清掉本账号的资料/愿望单缓存，避免他人登录时闪现旧账号数据
            profileState.resetForSessionChange()
            wishlistState.reset()
        }
    }

    // ---------- 内置更新 ----------
    val updateManager = UpdateManager(getApplication(), api, locator.apiClient, store)
    private val _updateState = MutableStateFlow<UpdateUiState>(UpdateUiState.Idle)
    val updateState: StateFlow<UpdateUiState> = _updateState
    private var lastUpdateInfo: UpdateManager.UpdateInfo? = null

    /** 启动 / 回前台自动检查（24h 节流）；「我的 → 检查更新」手动检查（不节流）。 */
    fun checkUpdate(manual: Boolean) {
        if (_updateState.value is UpdateUiState.Checking) return
        if (_updateState.value is UpdateUiState.Downloading) return
        viewModelScope.launch {
            if (!manual) {
                // 静默节流：24h 内已查过直接跳过（不打扰）
                val data = session.value
                if (System.currentTimeMillis() - data.lastUpdateCheckAt < UpdateManager.THROTTLE_MS) {
                    return@launch
                }
            }
            // 自动检查静默进行（不闪「检查中」弹窗）；手动检查显示 Checking 弹窗
            if (manual) _updateState.value = UpdateUiState.Checking
            val result = updateManager.check(
                manual = manual,
                ignoredVersionCode = session.value.ignoredVersionCode,
                lastCheckAt = session.value.lastUpdateCheckAt,
            )
            _updateState.value = when (result) {
                is UpdateManager.CheckResult.Available ->
                    UpdateUiState.Available(result.info, result.force)
                UpdateManager.CheckResult.None -> if (manual) UpdateUiState.Latest else UpdateUiState.Idle
                UpdateManager.CheckResult.Failed -> if (manual) UpdateUiState.CheckFailed else UpdateUiState.Idle
                is UpdateManager.CheckResult.Unavailable ->
                    if (manual) UpdateUiState.Unavailable(result.version) else UpdateUiState.Idle
            }
        }
    }

    /** 「立即更新」：Wi-Fi 默认，移动网络先问（BY §4.3）。 */
    fun onUpdateNow() {
        val current = _updateState.value as? UpdateUiState.Available ?: return
        if (updateManager.isWifi()) {
            startDownload(current.info)
        } else {
            _updateState.value = UpdateUiState.AskNetwork(current.info)
        }
    }

    fun onUseDataToDownload() {
        val current = _updateState.value as? UpdateUiState.AskNetwork ?: return
        startDownload(current.info)
    }

    fun onDismissUpdate() {
        _updateState.value = UpdateUiState.Idle
    }

    fun onIgnoreVersion() {
        val current = _updateState.value as? UpdateUiState.Available ?: return
        viewModelScope.launch {
            store.setIgnoredVersionCode(current.info.versionCode)
            _updateState.value = UpdateUiState.Idle
        }
    }

    private fun startDownload(info: UpdateManager.UpdateInfo) {
        lastUpdateInfo = info
        viewModelScope.launch {
            _updateState.value = UpdateUiState.Downloading(-1f)
            try {
                updateManager.downloadAndVerify(info) { done, total ->
                    val p = if (total > 0) done.toFloat() / total else -1f
                    _updateState.value = UpdateUiState.Downloading(p)
                }
                _updateState.value = UpdateUiState.Ready(info)
            } catch (e: Exception) {
                android.util.Log.e("SteamDealsUpdate", "download failed", e)
                val verifyFailed = e is IllegalStateException && e.message?.startsWith("sha256") == true
                _updateState.value = if (verifyFailed) {
                    UpdateUiState.VerifyFailed(e.message ?: "校验失败")
                } else {
                    UpdateUiState.DownloadFailed(e.message ?: "下载失败")
                }
            }
        }
    }

    /** 下载完成 → 调系统安装（PackageInstaller / ACTION_VIEW 确认）。 */
    fun onInstallNow() {
        val current = _updateState.value as? UpdateUiState.Ready ?: return
        val installed = updateManager.install(updateManager.apkFile())
        // 系统安装确认弹窗接管后续；对话框收起到 Idle
        _updateState.value = UpdateUiState.Idle
    }

    fun onRetryDownload() {
        val current = _updateState.value
        if (current is UpdateUiState.Ready) {
            // 已下载完成：直接再走安装
            onInstallNow()
            return
        }
        // 校验/下载失败：重新拉 latest 清单（避免复用陈旧 sha256），随后用户重新点「立即更新」
        lastUpdateInfo = null
        checkUpdate(manual = true)
    }
}
