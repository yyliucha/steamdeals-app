package cn.yyliucha.steamdeals.ui.notifications

import androidx.compose.ui.graphics.Color
import cn.yyliucha.steamdeals.ui.theme.DangerRed
import cn.yyliucha.steamdeals.ui.theme.InfoBlue
import cn.yyliucha.steamdeals.ui.theme.WarningOrange

/**
 * DC（站点 TODO DC，docs/APP-PARITY.md §65 CM-6）公告纯口径层，无 Android 依赖、JVM 单测直锁：
 * - 未读 = 所有 active 公告中不在 readIds 的（接口 activeOnly 只回 active，这里不再过滤）；
 * - 已读合并 = 服务端 readIds ∪ 游客本地 readIds（登录后合并口径）；
 * - 级别：critical 红 / warning 橙 / info 蓝（与 Web noticeLevelLabel 同映射）；
 * - 时间：服务端 ISO（UTC）→ 站点时区 Asia/Shanghai（UTC+8、无夏令时）展示。
 */
object AnnouncementsModel {

    /** 未读公告 id（保持列表顺序）；口径②：active 中不在 readIds 的。 */
    fun unreadIds(allIds: List<Long>, readIds: Set<Long>): List<Long> =
        allIds.filterNot { it in readIds }

    fun unreadCount(allIds: List<Long>, readIds: Set<Long>): Int =
        unreadIds(allIds, readIds).size

    /** 游客本地已读合并到服务端后的并集口径（幂等）。 */
    fun mergedReadIds(server: Collection<Long>, local: Collection<Long>): Set<Long> =
        server.toSet() + local.toSet()

    fun levelColor(level: String?): Color = when (level) {
        "critical" -> DangerRed
        "warning" -> WarningOrange
        else -> InfoBlue
    }

    fun levelLabel(level: String?): String = when (level) {
        "critical" -> "重要"
        "warning" -> "提醒"
        "info" -> "通知"
        else -> "通知"
    }

    /**
     * ISO 时间 → "yyyy-MM-dd HH:mm"（站点时区 Asia/Shanghai，UTC+8）。
     * 只解析前缀 yyyy-MM-ddTHH:mm（容忍毫秒 / Z / 空格分隔）；解析不了回退原串前 16 位
     * （与旧列表展示一致，不抛异常）。minSdk 24 下不依赖 java.time（同 PriceHistoryModel 约定）。
     */
    fun formatTime(iso: String?): String? {
        if (iso.isNullOrBlank()) return null
        val s = iso.trim()
        val fallback = s.take(16).replace('T', ' ')
        val date = s.take(10)
        val rest = s.drop(10).trimStart('T', 't', ' ')
        val hh = rest.take(2).toIntOrNull() ?: return fallback
        val mm = rest.drop(2).trimStart(':').take(2).toIntOrNull() ?: return fallback
        if (hh !in 0..23 || mm !in 0..59) return fallback
        val epoch = epochDay(date) ?: return fallback
        var hour = hh + 8
        var day = epoch
        if (hour >= 24) {
            hour -= 24
            day += 1
        }
        val (y, m, d) = civilFromDays(day)
        return "%04d-%02d-%02d %02d:%02d".format(y, m, d, hour, mm)
    }

    /** "YYYY-MM-DD" → epochDay（Howard Hinnant 算法，与 PriceHistoryModel.epochDay 同源）。 */
    fun epochDay(date: String): Long? {
        if (date.length != 10 || date[4] != '-' || date[7] != '-') return null
        val y = date.substring(0, 4).toIntOrNull() ?: return null
        val m = date.substring(5, 7).toIntOrNull() ?: return null
        val d = date.substring(8, 10).toIntOrNull() ?: return null
        if (m !in 1..12 || d !in 1..31) return null
        val yy = if (m <= 2) y - 1 else y
        val era = Math.floorDiv(yy, 400)
        val yoe = yy - era * 400
        val mp = (m + 9) % 12
        val doy = (153 * mp + 2) / 5 + d - 1
        val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
        return era * 146097 + doe - 719468L
    }

    /** epochDay → (年, 月, 日)（Howard Hinnant 逆算法；仅正数日数区间使用）。 */
    fun civilFromDays(z: Long): Triple<Int, Int, Int> {
        val zz = z + 719468
        val era = Math.floorDiv(zz, 146097)
        val doe = zz - era * 146097
        val yoe = (doe - doe / 1460 + doe / 36524 - doe / 146096) / 365
        val y = yoe + era * 400
        val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
        val mp = (5 * doy + 2) / 153
        val d = doy - (153 * mp + 2) / 5 + 1
        val m = if (mp < 10) mp + 3 else mp - 9
        return Triple((y + if (m <= 2) 1 else 0).toInt(), m.toInt(), d.toInt())
    }
}
