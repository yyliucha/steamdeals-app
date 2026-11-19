package cn.yyliucha.steamdeals.ui.notifications

import cn.yyliucha.steamdeals.data.api.AnnouncementDto
import cn.yyliucha.steamdeals.data.api.AnnouncementsResponse
import cn.yyliucha.steamdeals.data.store.SessionData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * EG（站点 TODO EG，docs/ANDROID-POLISH.md §EG）：公告数据网关缝——生产走 SteamDealsApi；
 * 单测注入可控假实现（fetch 挂起拦截复现「陈旧快照晚到」竞态）。只暴露状态需要的两个动作。
 */
interface AnnouncementsGateway {
    /** 拉取 active 公告列表 + 当前登录态口径的 readIds（游客时 readIds 恒为空数组，见站点契约）。 */
    suspend fun fetch(): AnnouncementsResponse

    /** 上报已读（登录 → POST /api/app/announcements/read；服务端幂等只写真实存在的 id）。 */
    suspend fun markRead(ids: List<Long>)
}

/**
 * DC（站点 TODO DC，APP-PARITY §65 CM-6）公告状态：全局一份，首页铃铛角标、「我的」入口、
 * 公告弹层共用。未读口径：所有 active 公告中不在 readIds 的（AnnouncementsModel）。
 * 登录用户 readIds 用服务端（跨设备跟随账号）；游客用本地 DataStore（重启保留）。
 *
 * EG（站点 docs/ANDROID-POLISH.md §EG）角标回跳修复，两处根因一起堵：
 * 1. refresh() 的陈旧快照晚到：打开弹层触发的列表请求在「标记已读」之后才返回时，其 readIds
 *    是标记前的快照——直接覆盖会把角标原地复活。修复：请求起点拍下「标记纪元 markEpoch」，
 *    返回时纪元变了 = 快照可能陈旧 → 只与内存并集、不覆盖（本地标记只会增加 id，并集永不复活角标）；
 *    纪元没变 = 快照权威 → 直落（跨设备已读正常同步）。
 * 2. 会话中途切换：请求期间登录/退出/换账号时，旧会话口径的快照作废——跳过应用，
 *    由新会话自己的 refresh（session.collect 必然触发）定口径，避免旧账号已读串到新账号。
 * 取代 DZ 的 markInFlight 布尔闩：闩只护住「标记请求在途」的窗口，护不住
 * 「标记 POST 已完成、更早发出的陈旧 GET 才返回」的窗口（EG 实测踩中的正是后者）。
 */
class AnnouncementsState(
    private val scope: CoroutineScope,
    private val session: StateFlow<SessionData>,
    private val gateway: AnnouncementsGateway,
    /** 游客本地已读落盘（DataStore）；登录态不用。 */
    private val persistLocalReads: suspend (List<Long>) -> Unit,
    /** 游客本地已读读取（DataStore）；登录后合并到服务端用。 */
    private val localReadIds: suspend () -> List<Long>,
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

    /** EG：标记纪元——每次 markRead/markAllRead 更新内存已读后 +1（见类注释的竞态分析）。 */
    private var markEpoch = 0L

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

    /** 拉列表 + 按当前登录态定已读口径（游客本地 / 登录服务端）。失败静默保留旧数据。
     *  EG：应用快照前核对「会话没换 + 期间没标记」——陈旧快照只并集不覆盖，换会话则作废。 */
    fun refresh() {
        scope.launch {
            loading.value = true
            // 请求起点拍快照：会话（决定 readIds 归属）与标记纪元（决定快照是否可能陈旧）。
            val sessionAtStart = session.value
            val epochAtStart = markEpoch
            try {
                val resp = gateway.fetch()
                announcements.value = resp.announcements
                val snapshot: Set<Long> = if (sessionAtStart.isLoggedIn) {
                    resp.readIds.toSet()
                } else {
                    sessionAtStart.readAnnouncementIds.toSet()
                }
                val sameSession = session.value.isLoggedIn == sessionAtStart.isLoggedIn &&
                    session.value.user?.id == sessionAtStart.user?.id
                if (!sameSession) {
                    // 会话切换：这份快照作废；新会话由 session.collect 触发的 refresh 定口径
                    return@launch
                }
                readIds.value = if (epochAtStart == markEpoch) {
                    snapshot // 无本地标记 → 快照权威，直落
                } else {
                    readIds.value + snapshot // 期间有标记 → 并集，不复活角标
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
        markEpoch += 1
        scope.launch {
            // 按运行时的登录态决定口径（调用与执行之间可能发生登录/退出切换）
            if (session.value.isLoggedIn) {
                runCatching { gateway.markRead(listOf(id)) }
            } else {
                runCatching { persistLocalReads(readIds.value.toList()) }
            }
        }
    }

    /** DZ/EG：全部已读——当前 active 未读一次批量上报（登录服务端 / 游客本地 DataStore）。
     *  先改内存 → 角标立即消失；刷新/重进/重启后由持久化口径兜底不复现。 */
    fun markAllRead() {
        val unread = AnnouncementsModel.unreadIds(announcements.value.map { it.id }, readIds.value)
        if (unread.isEmpty()) return
        readIds.value = readIds.value + unread
        markEpoch += 1
        scope.launch {
            if (session.value.isLoggedIn) {
                runCatching { gateway.markRead(unread) }
            } else {
                runCatching { persistLocalReads(readIds.value.toList()) }
            }
        }
    }

    /**
     * 游客本地已读合并到服务端（登录成功后调用一次）：本地 ids 全部 POST，
     * 服务端按真实存在公告过滤、on conflict 幂等；失败静默——本地不丢，下次登录再试。
     */
    suspend fun mergeLocalReadsToServer() {
        val local = localReadIds()
        if (local.isEmpty()) return
        runCatching { gateway.markRead(local) }
    }
}
