package cn.yyliucha.steamdeals

import cn.yyliucha.steamdeals.data.api.SeriesPoint
import cn.yyliucha.steamdeals.ui.components.PriceHistoryModel
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/**
 * CM-3 单测：价格历史区间与指标，口径 = Web（frontend/src/components/PriceHistoryReport.vue，
 * docs/PRICE-HISTORY-REPORT.md §11）。夹具用 java.time.LocalDate 生成（测试跑在 JVM），
 * 与主代码手写 epochDay 互相印证。
 * 反向自检：① filterRange 的 `> end - days` 改成 `>=` → 「区间过滤」边界用例红；
 *           ② curVsHist 的 `<=` 改成 `<` → 「已是史低」用例红；
 *           ③ histLow 改成区间内 min（而非全期）→ 「史低是全期口径」用例红。
 */
class PriceHistoryModelTest {

    private fun p(day: String, cents: Long) =
        SeriesPoint(day = day, price_cents = cents, currency = "CNY")

    /** 从 start 起连续 n 天的 series；price(i) 给第 i 天定价。 */
    private fun buildSeries(start: String, n: Int, price: (Int) -> Long): List<SeriesPoint> {
        val s = LocalDate.parse(start)
        return (0 until n).map { i ->
            SeriesPoint(day = s.plusDays(i.toLong()).toString(), price_cents = price(i), currency = "CNY")
        }
    }

    @Test
    fun `epochDay 与 JDK LocalDate 日历一致`() {
        // 守的约束：手写 epochDay 必须与 JDK 日历一致（否则 30/90 天过滤整体错位）
        assertEquals(LocalDate.parse("2026-11-17").toEpochDay(), PriceHistoryModel.epochDay("2026-11-17"))
        assertEquals(LocalDate.parse("2024-02-29").toEpochDay(), PriceHistoryModel.epochDay("2024-02-29"))
        assertEquals(LocalDate.parse("2026-01-01").toEpochDay(), PriceHistoryModel.epochDay("2026-01-01"))
    }

    @Test
    fun `区间过滤 30-90-全部 与 Web 边界一致`() {
        // 守的约束：末日 2026-11-17；近 30 天 = day > 2026-10-18（严格大于）→ 首条 2026-10-19、共 30 条；
        // 近 90 天 = day > 2026-08-19 → 首条 2026-08-20、共 90 条；全部 = 原样。
        val series = buildSeries("2026-01-01", 321) { 100L } // 2026-01-01 ~ 2026-11-17
        assertEquals("2026-11-17", series.last().day)

        val r30 = PriceHistoryModel.filterRange(series, PriceHistoryModel.RANGE_30)
        assertEquals(30, r30.size)
        assertEquals("2026-10-19", r30.first().day)
        assertEquals("2026-11-17", r30.last().day)

        val r90 = PriceHistoryModel.filterRange(series, PriceHistoryModel.RANGE_90)
        assertEquals(90, r90.size)
        assertEquals("2026-08-20", r90.first().day)

        assertEquals(321, PriceHistoryModel.filterRange(series, PriceHistoryModel.RANGE_ALL).size)
    }

    @Test
    fun `史低是全期口径 区间切换不变`() {
        // 守的约束：史低出现在 2026-08-11（60 天前），近 30 天区间不含它，但 histLow 仍是全期史低 9900；
        // 区间最低随区间变化（近 30 天 = 19800），这是 Web「史低口径不变」的核心语义。
        val series = buildSeries("2026-08-01", 120) { i ->
            when {
                i == 10 -> 9900L   // 2026-08-11 史低
                i >= 60 -> 19800L  // 2026-09-30 起
                else -> 14800L
            }
        }
        assertEquals(9900L, PriceHistoryModel.histLow(series)!!)
        assertEquals(listOf("2026-08-11"), PriceHistoryModel.lowDays(series))

        val r30 = PriceHistoryModel.filterRange(series, PriceHistoryModel.RANGE_30)
        assertEquals(19800L, PriceHistoryModel.rangeMin(r30)!!)
        assertEquals(19800L, PriceHistoryModel.rangeMax(r30)!!)
        // 全部区间的最低 = 史低
        val all = PriceHistoryModel.filterRange(series, PriceHistoryModel.RANGE_ALL)
        assertEquals(9900L, PriceHistoryModel.rangeMin(all)!!)
        assertEquals(19800L, PriceHistoryModel.rangeMax(all)!!)
    }

    @Test
    fun `当前较史低 三态`() {
        // 守的约束：cur <= histLow →「已是史低」；cur > histLow → +X.X%（1 位小数、Locale.US）；缺值 → —
        assertEquals("已是史低", PriceHistoryModel.curVsHist(100L, 100L))
        assertEquals("已是史低", PriceHistoryModel.curVsHist(90L, 100L))
        assertEquals("+50.0%", PriceHistoryModel.curVsHist(150L, 100L))
        assertEquals("+33.3%", PriceHistoryModel.curVsHist(400L, 300L))
        assertEquals("—", PriceHistoryModel.curVsHist(null, 100L))
        assertEquals("—", PriceHistoryModel.curVsHist(100L, null))
    }

    @Test
    fun `记录天数与区间指标都基于过滤后序列`() {
        // 守的约束：记录天数 = 区间条数；区间最低/最高 = 区间内 min/max
        val series = buildSeries("2026-01-01", 321) { 100L }
        val r30 = PriceHistoryModel.filterRange(series, PriceHistoryModel.RANGE_30)
        assertEquals(30, r30.size)
        assertEquals(100L, PriceHistoryModel.rangeMin(r30)!!)
        assertEquals(100L, PriceHistoryModel.rangeMax(r30)!!)
    }

    @Test
    fun `空序列安全`() {
        assertEquals(emptyList<SeriesPoint>(), PriceHistoryModel.filterRange(emptyList(), PriceHistoryModel.RANGE_90))
        assertEquals(null, PriceHistoryModel.histLow(emptyList()))
        assertEquals(emptyList<String>(), PriceHistoryModel.lowDays(emptyList()))
        assertEquals(null, PriceHistoryModel.rangeMin(emptyList()))
        assertEquals(null, PriceHistoryModel.rangeMax(emptyList()))
    }

    @Test
    fun `平线区间最低等于最高`() {
        // 守的约束：区间内只有一个价格时 min == max（图表平线居中的前提）
        val series = buildSeries("2026-11-01", 17) { 19800L }
        assertEquals(19800L, PriceHistoryModel.rangeMin(series)!!)
        assertEquals(19800L, PriceHistoryModel.rangeMax(series)!!)
    }
}
