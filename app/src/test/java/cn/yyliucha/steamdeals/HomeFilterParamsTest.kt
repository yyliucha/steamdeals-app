package cn.yyliucha.steamdeals

import cn.yyliucha.steamdeals.ui.home.HomeFilter
import cn.yyliucha.steamdeals.ui.home.HomeFilterParams
import cn.yyliucha.steamdeals.ui.home.HomeSort
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * DA 批（站点 docs/APP-FILTER-SERVER-SIDE.md §2/§3）单测：首页筛选/排序 → 服务端参数映射。
 * 锁住的约束：
 *   1) 折扣/史低/免费/未发售 → discountOnly/histLowOnly/freeOnly/unreleased=1（开关互斥由服务端归一）；
 *   2) 排序 heat/release/price → sort=heat/release/price；
 *   3) 「已拥有」chip → owned=only；其余 chip（含全部）沿用 CV 批口径 owned=include（不排除已拥有）；
 *   4) page/pageSize 透传（分页重置由调用方把 page=1 传入）。
 * 反向自检：把 filterParam(DISCOUNT) 的 key 改成 "histLowOnly" → 「折扣 chip 映射」用例红。
 */
class HomeFilterParamsTest {

    @Test
    fun `折扣 chip 映射 discountOnly 开关`() {
        assertEquals("discountOnly", HomeFilterParams.filterParam(HomeFilter.DISCOUNT).first)
        assertEquals("1", HomeFilterParams.filterParam(HomeFilter.DISCOUNT).second)
    }

    @Test
    fun `史低 chip 映射 histLowOnly 开关`() {
        assertEquals("histLowOnly", HomeFilterParams.filterParam(HomeFilter.HIST_LOW).first)
        assertEquals("1", HomeFilterParams.filterParam(HomeFilter.HIST_LOW).second)
    }

    @Test
    fun `免费 chip 映射 freeOnly 开关`() {
        assertEquals("freeOnly", HomeFilterParams.filterParam(HomeFilter.FREE).first)
        assertEquals("1", HomeFilterParams.filterParam(HomeFilter.FREE).second)
    }

    @Test
    fun `未发售 chip 映射 unreleased 开关`() {
        assertEquals("unreleased", HomeFilterParams.filterParam(HomeFilter.UPCOMING).first)
        assertEquals("1", HomeFilterParams.filterParam(HomeFilter.UPCOMING).second)
    }

    @Test
    fun `已拥有 chip 用 owned=only`() {
        assertEquals("owned", HomeFilterParams.filterParam(HomeFilter.OWNED).first)
        assertEquals("only", HomeFilterParams.filterParam(HomeFilter.OWNED).second)
    }

    @Test
    fun `全部 chip 沿用 CV 口径 owned=include`() {
        assertEquals("owned", HomeFilterParams.filterParam(HomeFilter.ALL).first)
        assertEquals("include", HomeFilterParams.filterParam(HomeFilter.ALL).second)
    }

    @Test
    fun `三种排序映射服务端 sort 值`() {
        assertEquals("heat", HomeFilterParams.sortParam(HomeSort.HEAT))
        assertEquals("release", HomeFilterParams.sortParam(HomeSort.RELEASE))
        assertEquals("price", HomeFilterParams.sortParam(HomeSort.PRICE))
    }

    @Test
    fun `请求参数完整且分页透传`() {
        val params = HomeFilterParams.paramsFor(HomeFilter.DISCOUNT, HomeSort.RELEASE, page = 3, pageSize = 50)
        assertEquals("cn", params["region"])
        assertEquals("3", params["page"])
        assertEquals("50", params["pageSize"])
        assertEquals("release", params["sort"])
        assertEquals("1", params["discountOnly"])
        assertEquals("include", params["owned"])
        // FG（站点 docs/REMOVE-18R-ENTRY.md）：18R 筛选入口已移除——请求固定 adult=hide，不再带 rating
        assertEquals("hide", params["adult"])
        assertFalse(params.containsKey("rating"))
        assertFalse(params.containsKey("histLowOnly"))
        assertFalse(params.containsKey("unreleased"))
    }

    @Test
    fun `已拥有请求 owned=only 且不带其它开关`() {
        val params = HomeFilterParams.paramsFor(HomeFilter.OWNED, HomeSort.HEAT, page = 1, pageSize = 50)
        assertEquals("only", params["owned"])
        assertEquals("heat", params["sort"])
        assertEquals("1", params["page"])
        assertEquals("chip 共 6 个（全部/折扣/史低/免费/未发售/已拥有）", 6, HomeFilter.entries.size)
    }

    // ---- FG（站点 docs/REMOVE-18R-ENTRY.md）：18R 筛选入口已移除 ----
    // 反向自检：把 paramsFor 里的 put("adult", "hide") 改回 "badge" → 用例红。

    @Test
    fun `FG 首页请求固定 adult=hide 且不带 rating（无 18R 查询入口）`() {
        val params = HomeFilterParams.paramsFor(HomeFilter.ALL, HomeSort.HEAT, page = 1, pageSize = 50)
        assertEquals("hide", params["adult"])
        assertFalse("不再带 rating 参数（18R 筛选已移除）", params.containsKey("rating"))
    }

    @Test
    fun `FG 全部筛选组合都固定 adult=hide（不含成人查询开关）`() {
        for (f in HomeFilter.entries) {
            val params = HomeFilterParams.paramsFor(f, HomeSort.HEAT, page = 1, pageSize = 50)
            assertEquals("筛选 ${f.name} 固定过滤成人内容", "hide", params["adult"])
        }
    }
}
