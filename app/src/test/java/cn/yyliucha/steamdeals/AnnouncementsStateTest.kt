package cn.yyliucha.steamdeals

import cn.yyliucha.steamdeals.data.api.AnnouncementDto
import cn.yyliucha.steamdeals.data.api.AnnouncementsResponse
import cn.yyliucha.steamdeals.data.api.UserDto
import cn.yyliucha.steamdeals.data.store.SessionData
import cn.yyliucha.steamdeals.ui.notifications.AnnouncementsGateway
import cn.yyliucha.steamdeals.ui.notifications.AnnouncementsState
import java.util.ArrayDeque
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * EG（站点 TODO EG，docs/ANDROID-POLISH.md §EG）竞态单测：AnnouncementsState 与网络解耦后
 * （AnnouncementsGateway 缝），用可控假网关精确复现「打开弹层触发的列表请求在标记已读之后
 * 才返回」的陈旧快照晚到场景，并锁住三条行为：
 * 1. 标记在途/完成后，陈旧快照只并集不覆盖 → 角标不回跳（B 递减 / C 归零保持）；
 * 2. 无本地标记时快照权威直落（替换）→ 跨设备已读正常同步、删公告后旧 id 不再残留；
 * 3. 请求期间登录态切换 → 旧会话快照作废，不把旧账号已读串进新账号。
 * 反向自检：把 refresh() 的「并集」改回无条件直落 → 用例 1/2 的角标断言红；
 * 去掉会话守卫 → 用例 3 红；游客持久化断言见用例 4。
 */
class AnnouncementsStateTest {

    private val dto1 = AnnouncementDto(id = 1, title = "公告一")
    private val dto2 = AnnouncementDto(id = 2, title = "公告二")

    /** 可控假网关：fetch 逐个消费挂起闸门——测试手动放行，精确复现「陈旧快照晚到」。 */
    private class FakeGateway : AnnouncementsGateway {
        val gates = ArrayDeque<CompletableDeferred<AnnouncementsResponse>>()
        val marked = mutableListOf<List<Long>>()

        fun nextFetch(): CompletableDeferred<AnnouncementsResponse> {
            val gate = CompletableDeferred<AnnouncementsResponse>()
            gates.addLast(gate)
            return gate
        }

        override suspend fun fetch(): AnnouncementsResponse = gates.removeFirst().await()

        override suspend fun markRead(ids: List<Long>) {
            marked.add(ids.toList())
        }
    }

    /** scope 用 backgroundScope：session.collect 常驻不阻塞 runTest 收尾（随测试结束自动取消）。 */
    private fun TestScope.newState(
        session: MutableStateFlow<SessionData>,
        gateway: FakeGateway,
        persisted: MutableList<List<Long>>,
    ) = AnnouncementsState(
        scope = backgroundScope,
        session = session,
        gateway = gateway,
        persistLocalReads = { persisted.add(it.toList()) },
        localReadIds = { session.value.readAnnouncementIds },
    )

    private fun loggedIn() = SessionData(token = "t", user = UserDto(id = 7, username = "alice"))

    /** 首拉两条未读公告（init 触发的 refresh 用第一个闸门放行）。 */
    private fun TestScope.prime(
        gateway: FakeGateway,
        session: MutableStateFlow<SessionData>,
        persisted: MutableList<List<Long>>,
    ): AnnouncementsState {
        val gate1 = gateway.nextFetch()
        val state = newState(session, gateway, persisted)
        runCurrent()
        gate1.complete(
            AnnouncementsResponse(
                announcements = listOf(dto1, dto2),
                readIds = emptyList(),
            ),
        )
        runCurrent()
        assertEquals(2, state.unreadCount.value)
        return state
    }

    // ---- EG-B：点开详情标记在途时，先发的列表快照返回不得复活角标 ----

    @Test
    fun `EG-B 标记已读后晚到的陈旧快照不得复活角标`() = runTest {
        val gateway = FakeGateway()
        val persisted = mutableListOf<List<Long>>()
        val session = MutableStateFlow(loggedIn())
        val state = prime(gateway, session, persisted)

        // 打开弹层 → 列表请求挂起（其快照将在标记之后才返回）
        val gate2 = gateway.nextFetch()
        state.open()
        runCurrent()

        // 点开详情标记单条已读：内存 +1、纪元 +1、服务端上报完成
        state.markRead(1)
        runCurrent()
        assertEquals(1, state.unreadCount.value)
        assertEquals(listOf(listOf(1L)), gateway.marked)

        // 陈旧快照（readIds 为空 = 标记前的服务端状态）晚到 → 只并集不覆盖
        gate2.complete(
            AnnouncementsResponse(
                announcements = listOf(dto1, dto2),
                readIds = emptyList(),
            ),
        )
        runCurrent()
        assertEquals(setOf(1L), state.readIds.value)
        assertEquals(1, state.unreadCount.value)
    }

    // ---- EG-C：全部已读后晚到的陈旧快照不得复活角标（归零保持） ----

    @Test
    fun `EG-C 全部已读后晚到的陈旧快照角标保持归零`() = runTest {
        val gateway = FakeGateway()
        val persisted = mutableListOf<List<Long>>()
        val session = MutableStateFlow(loggedIn())
        val state = prime(gateway, session, persisted)

        val gate2 = gateway.nextFetch()
        state.open()
        runCurrent()

        state.markAllRead()
        runCurrent()
        assertEquals(0, state.unreadCount.value)
        assertEquals(listOf(listOf(1L, 2L)), gateway.marked)

        gate2.complete(
            AnnouncementsResponse(
                announcements = listOf(dto1, dto2),
                readIds = emptyList(),
            ),
        )
        runCurrent()
        assertEquals(setOf(1L, 2L), state.readIds.value)
        assertEquals(0, state.unreadCount.value)
    }

    // ---- 无本地标记时快照权威直落（替换而非并集，跨设备口径） ----

    @Test
    fun `EG 无本地标记时快照权威直落_跨设备已读同步`() = runTest {
        val gateway = FakeGateway()
        val persisted = mutableListOf<List<Long>>()
        val session = MutableStateFlow(loggedIn())
        val gate1 = gateway.nextFetch()
        val state = newState(session, gateway, persisted)
        runCurrent()
        gate1.complete(AnnouncementsResponse(listOf(dto1, dto2), listOf(1L, 2L)))
        runCurrent()
        assertEquals(setOf(1L, 2L), state.readIds.value)
        assertEquals(0, state.unreadCount.value)

        // 另一设备标记了 2；公告 1 被管理员删除 → 服务端快照只剩 {2}
        val gate2 = gateway.nextFetch()
        state.refresh()
        runCurrent()
        gate2.complete(AnnouncementsResponse(listOf(dto2), listOf(2L)))
        runCurrent()
        assertEquals(setOf(2L), state.readIds.value)
        assertEquals(0, state.unreadCount.value)
    }

    // ---- 请求期间登录态切换：旧会话快照作废 ----

    @Test
    fun `EG 请求期间退出登录_旧账号快照作废`() = runTest {
        val gateway = FakeGateway()
        val persisted = mutableListOf<List<Long>>()
        val session = MutableStateFlow(loggedIn())
        val gate1 = gateway.nextFetch()
        val state = newState(session, gateway, persisted)
        runCurrent()
        gate1.complete(AnnouncementsResponse(emptyList(), listOf(5L)))
        runCurrent()
        assertEquals(setOf(5L), state.readIds.value)

        // 打开弹层 → 登录口径请求挂起期间退出登录（旧快照将携带 9，与当前内存 5 不同，可辨别是否被应用）
        val gate2 = gateway.nextFetch()
        state.open()
        runCurrent()
        session.value = SessionData() // 游客：本地已读为空
        val gate3 = gateway.nextFetch() // 先备好游客口径 refresh 的闸门
        runCurrent() // session.collect 触发游客 refresh → 消费 gate3；open 的旧请求仍挂在 gate2

        // 旧登录快照（readIds=[9]）晚到 → 作废不应用（内存仍保持退出前的 {5}，等游客 refresh 定口径）
        gate2.complete(AnnouncementsResponse(emptyList(), listOf(9L)))
        runCurrent()
        assertEquals(setOf(5L), state.readIds.value)

        // 游客 refresh 快照落定（本地口径空）→ 旧账号已读全部出清
        gate3.complete(AnnouncementsResponse(listOf(dto1), emptyList()))
        runCurrent()
        assertEquals(emptySet<Long>(), state.readIds.value)
        assertEquals(1, state.unreadCount.value)
    }

    // ---- EG ④：游客本地 DataStore 写入 ----

    @Test
    fun `EG 游客标记已读写本地口径且不调服务端`() = runTest {
        val gateway = FakeGateway()
        val persisted = mutableListOf<List<Long>>()
        val session = MutableStateFlow(SessionData())
        val state = prime(gateway, session, persisted)

        state.markRead(1)
        runCurrent()
        assertEquals(1, state.unreadCount.value)
        assertEquals(listOf(listOf(1L)), persisted)
        assertTrue(gateway.marked.isEmpty())

        state.markAllRead()
        runCurrent()
        assertEquals(0, state.unreadCount.value)
        assertEquals(listOf(listOf(1L), listOf(1L, 2L)), persisted)
        assertTrue(gateway.marked.isEmpty())

        // 幂等：无未读再点「全部已读」不再写
        val before = persisted.size
        state.markAllRead()
        runCurrent()
        assertEquals(before, persisted.size)
    }
}
