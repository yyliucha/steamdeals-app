package cn.yyliucha.steamdeals.ui.components

import cn.yyliucha.steamdeals.data.api.SeriesPoint
import java.util.Locale

/**
 * CM-3（docs/APP-PARITY.md §3）：价格历史区间与指标，口径与 Web 完全一致
 * （frontend/src/components/PriceHistoryReport.vue + docs/PRICE-HISTORY-REPORT.md §11）：
 * - 区间 30/90 按「最后一条 day 往前 N 天」过滤（边界严格大于；series 按天升序）；
 * - 史低 = 全期最低；低点 = 全期价格等于史低的全部日期（区间切换不改史低口径）；
 * - 区间最低/最高 = 区间内 min/max；记录天数 = 区间条数；
 * - 当前较史低：cur <= histLow →「已是史低」，否则 +X.X%（1 位小数，Locale.US 防小数逗号）。
 * 纯 Kotlin、无 Android 依赖，JVM 单测直接锁口径。
 */
object PriceHistoryModel {
    const val RANGE_30 = "30"
    const val RANGE_90 = "90"
    const val RANGE_ALL = "all"
    val RANGES = listOf(RANGE_30, RANGE_90, RANGE_ALL)
    const val DEFAULT_RANGE = RANGE_90

    /** "YYYY-MM-DD" → epochDay（Howard Hinnant 算法；minSdk 24 下不可依赖 java.time）。 */
    fun epochDay(day: String): Long {
        val y = day.substring(0, 4).toInt()
        val m = day.substring(5, 7).toInt()
        val d = day.substring(8, 10).toInt()
        val yy = if (m <= 2) y - 1 else y
        val era = Math.floorDiv(yy, 400)
        val yoe = yy - era * 400
        val mp = (m + 9) % 12
        val doy = (153 * mp + 2) / 5 + d - 1
        val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
        return era * 146097 + doe - 719468L
    }

    /**
     * Web 语义（PriceHistoryReport.vue filtered）：range=30/90 时保留
     * `epochDay(day) > epochDay(lastDay) - N`（即最近 N 天内，严格大于）；all 返回全部。
     */
    fun filterRange(series: List<SeriesPoint>, range: String): List<SeriesPoint> {
        val last = series.lastOrNull() ?: return emptyList()
        if (range == RANGE_ALL) return series
        val days = if (range == RANGE_30) 30 else 90
        val end = epochDay(last.day)
        return series.filter { epochDay(it.day) > end - days }
    }

    /** 史低 = 全期最低（全量 series，不随区间变化）。 */
    fun histLow(series: List<SeriesPoint>): Long? = series.mapNotNull { it.price_cents }.minOrNull()

    /** 史低出现的全部日期（全期口径）。 */
    fun lowDays(series: List<SeriesPoint>): List<String> {
        val hl = histLow(series) ?: return emptyList()
        return series.filter { it.price_cents == hl }.map { it.day }
    }

    fun rangeMin(filtered: List<SeriesPoint>): Long? = filtered.mapNotNull { it.price_cents }.minOrNull()
    fun rangeMax(filtered: List<SeriesPoint>): Long? = filtered.mapNotNull { it.price_cents }.maxOrNull()

    /** 当前较史低：cur 取游戏当前最低价（game.current_low），与 Web props.currentLow 同口径。 */
    fun curVsHist(currentLow: Long?, histLow: Long?): String {
        if (currentLow == null || histLow == null) return "—"
        if (currentLow <= histLow) return "已是史低"
        val pct = (currentLow - histLow) * 100.0 / histLow
        return "+" + String.format(Locale.US, "%.1f", pct) + "%"
    }
}
