package cn.yyliucha.steamdeals.ui.notifications

import cn.yyliucha.steamdeals.data.api.AnnouncementDto
import cn.yyliucha.steamdeals.data.api.SteamDealsApi
import cn.yyliucha.steamdeals.data.store.SessionData
import cn.yyliucha.steamdeals.data.store.SessionStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * DC（站点 TODO DC，APP-PARITY §65 CM-6）公告状态：全局一份，首页铃铛角标、「我的」入口、
 * 公告弹层共用。未读口径：所有 active 公告中不在 readIds 的（AnnouncementsModel）。
 * 登录用户 readIds 用服务端（跨设备跟随账号）；游客用本地 DataStore（重启保留）。
 */
class AnnouncementsState(
    private val api: SteamDealsApi,
    private val store: SessionStore,
    private val session: StateFlow<SessionData>,
    private val scope: CoroutineScope,
) {
    val announcements = MutableStateFlow<List<AnnouncementDto>>(emptyList())

    /** 当前生效已读 id（登录 = 服务端；游客 = 本地）。 */
    val readIds = MutableStateFlow<Set<Long>>(emptySet())

    val loading = MutableStateFlow(false)

    /** 角标数：0 隐藏由 UI 决定。 */
    val unreadCount: StateFlow<Int> =
        combine(announcements, readIds) { list, read ->
            AnnouncementsModel.unreadCount(list.map { it.id }, read)
        }.stateIn(scope, SharingStarted.Eagerly, 0)

    /** 公告列表弹层开关（首页铃铛 / 「我的」入口共用）。 */
    val overlayVisible = MutableStateFlow(false)

    init {
        // 登录 / 退出切换 → 已读口径切换（服务端 ↔ 本地），重新拉列表。
        // 初始值也会触发一次 → App 启动即拉公告，首页角标无需等用户操作。
        scope.launch {
            session.collect { refresh() }
        }
    }

    fun open() {
        overlayVisible.value = true
        refresh()
    }

    fun close() {
        overlayVisible.value = false
    }

    /** 拉列表 + 按当前登录态定已读口径（游客本地 / 登录服务端）。失败静默保留旧数据。 */
    fun refresh() {
        scope.launch {
            loading.value = true
            try {
                val resp = api.announcements()
                announcements.value = resp.announcements
                readIds.value = if (session.value.isLoggedIn) {
                    resp.readIds.toSet()
                } else {
                    session.value.readAnnouncementIds.toSet()
                }
            } catch (_: Exception) {
                // 网络失败不打扰（角标沿用上次数据）；下拉刷新可重试
            } finally {
                loading.value = false
            }
        }
    }

    /** 标记单条已读：先改内存（角标即时递减），再上报——登录 → 服务端；游客 → 本地。 */
    fun markRead(id: Long) {
        if (id in readIds.value) return
        readIds.value = readIds.value + id
        if (session.value.isLoggedIn) {
            scope.launch { runCatching { api.markAnnouncementsRead(listOf(id)) } }
        } else {
            scope.launch { store.setReadAnnouncementIds(readIds.value.toList()) }
        }
    }

    /**
     * 游客本地已读合并到服务端（登录成功后调用一次）：本地 ids 全部 POST，
     * 服务端按真实存在公告过滤、on conflict 幂等；失败静默——本地不丢，下次登录再试。
     */
    suspend fun mergeLocalReadsToServer() {
        val local = store.data.firstOrNull()?.readAnnouncementIds.orEmpty()
        if (local.isEmpty()) return
        runCatching { api.markAnnouncementsRead(local) }
    }
}
