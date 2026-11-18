package cn.yyliucha.steamdeals

import cn.yyliucha.steamdeals.ui.notifications.AnnouncementsModel
import cn.yyliucha.steamdeals.ui.theme.DangerRed
import cn.yyliucha.steamdeals.ui.theme.InfoBlue
import cn.yyliucha.steamdeals.ui.theme.WarningOrange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * DC（站点 TODO DC，APP-PARITY §65 CM-6）单测：公告纯口径——
 * 未读 = active 中不在 readIds 的；合并 = 服务端 ∪ 本地；级别映射；UTC→Asia/Shanghai 时间。
 * 反向自检：把 unreadIds 的 filterNot 改成 filter → 全部未读用例红；
 * 把 formatTime 的 +8 改成 +0 → 「北京 10:00」用例红。
 */
class AnnouncementsModelTest {

    // ---- 未读口径 ----

    @Test
    fun `未读等于全部active减去readIds`() {
        assertEquals(
            listOf(1L, 3L),
            AnnouncementsModel.unreadIds(listOf(1L, 2L, 3L), setOf(2L)),
        )
        assertEquals(2, AnnouncementsModel.unreadCount(listOf(1L, 2L, 3L), setOf(2L)))
    }

    @Test
    fun `全部已读未读为零`() {
        assertEquals(0, AnnouncementsModel.unreadCount(listOf(1L, 2L), setOf(1L, 2L, 99L)))
    }

    @Test
    fun `readIds里有过期公告id不影响计数`() {
        // 已删除公告的历史已读 id 不该让 active 公告凭空变未读
        assertEquals(0, AnnouncementsModel.unreadCount(listOf(5L), setOf(5L, 999L)))
        assertEquals(1, AnnouncementsModel.unreadCount(listOf(5L), setOf(999L)))
    }

    @Test
    fun `无公告未读为零`() {
        assertEquals(0, AnnouncementsModel.unreadCount(emptyList(), emptySet()))
    }

    // ---- 登录合并口径 ----

    @Test
    fun `合并为服务端与本地并集且幂等`() {
        assertEquals(
            setOf(1L, 2L, 3L),
            AnnouncementsModel.mergedReadIds(listOf(1L, 2L), listOf(2L, 3L)),
        )
        assertEquals(setOf(1L), AnnouncementsModel.mergedReadIds(listOf(1L), emptyList()))
        assertEquals(setOf(2L), AnnouncementsModel.mergedReadIds(emptyList(), listOf(2L)))
    }

    // ---- 级别映射（与 Web noticeLevelLabel 同映射）----

    @Test
    fun `级别颜色与文案映射`() {
        assertEquals(DangerRed, AnnouncementsModel.levelColor("critical"))
        assertEquals(WarningOrange, AnnouncementsModel.levelColor("warning"))
        assertEquals(InfoBlue, AnnouncementsModel.levelColor("info"))
        assertEquals(InfoBlue, AnnouncementsModel.levelColor(null)) // 未知回退 info
        assertEquals("重要", AnnouncementsModel.levelLabel("critical"))
        assertEquals("提醒", AnnouncementsModel.levelLabel("warning"))
        assertEquals("通知", AnnouncementsModel.levelLabel("info"))
        assertEquals("通知", AnnouncementsModel.levelLabel("别的"))
    }

    // ---- 时间：UTC → Asia/Shanghai（+8，无夏令时）----

    @Test
    fun `时间加八小时显示`() {
        assertEquals("2026-11-18 10:00", AnnouncementsModel.formatTime("2026-11-18T02:00:00.000Z"))
        assertEquals("2026-11-18 10:00", AnnouncementsModel.formatTime("2026-11-18T02:00:00Z"))
        assertEquals("2026-11-18 10:00", AnnouncementsModel.formatTime("2026-11-18 02:00"))
    }

    @Test
    fun `时间跨日进位`() {
        assertEquals("2026-12-01 07:30", AnnouncementsModel.formatTime("2026-11-30T23:30:00Z"))
    }

    @Test
    fun `时间跨年进位`() {
        assertEquals("2027-01-01 07:00", AnnouncementsModel.formatTime("2026-12-31T23:00:00Z"))
    }

    @Test
    fun `时间闰年进位`() {
        assertEquals("2028-02-29 04:00", AnnouncementsModel.formatTime("2028-02-28T20:00:00Z"))
    }

    @Test
    fun `时间为空或不可解析时安全回退`() {
        assertNull(AnnouncementsModel.formatTime(null))
        assertNull(AnnouncementsModel.formatTime(""))
        assertNull(AnnouncementsModel.formatTime("   "))
        assertEquals("乱写的", AnnouncementsModel.formatTime("乱写的"))
        assertEquals("2026-11-18", AnnouncementsModel.formatTime("2026-11-18"))
    }
}
