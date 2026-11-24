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
        // FI（站点 docs/ADMIN-RATING-FILTER.md）：adult 恒传站点策略（默认 hide=服务端过滤）；未选中 18R 不带 rating
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

    // ---- FI（站点 docs/ADMIN-RATING-FILTER.md）：18R 按站点策略控制 ----
    // hide=无入口、adult=hide 过滤成人（本地开关残留也不出成人）；
    // badge/show=显示入口（adult=策略值不过滤），选中 18R（adultEnabled）→ rating=adult 只看 18R。
    // 反向自检：把 paramsFor 里 adult 的站点策略归一改成无条件 "badge" → 以下 hide 用例红。

    @Test
    fun `FI hide 策略固定 adult=hide 且开关残留不带 rating（无入口）`() {
        for (f in HomeFilter.entries) {
            val params = HomeFilterParams.paramsFor(f, HomeSort.HEAT, page = 1, pageSize = 50, adultEnabled = false, adultDisplay = "hide")
            assertEquals("筛选 ${f.name} 过滤成人内容", "hide", params["adult"])
            assertFalse("未选中 18R 不带 rating", params.containsKey("rating"))
        }
    }

    @Test
    fun `FI hide 策略下残留 adultEnabled=true 仍固定 adult=hide 不带 rating`() {
        val params = HomeFilterParams.paramsFor(HomeFilter.ALL, HomeSort.HEAT, page = 1, pageSize = 50, adultEnabled = true, adultDisplay = "hide")
        assertEquals("hide", params["adult"])
        assertFalse("站点 hide 时本地开关不生效", params.containsKey("rating"))
    }

    @Test
    fun `FI badge 策略未选中 adult=badge 不带 rating（显示全部含成人）`() {
        val params = HomeFilterParams.paramsFor(HomeFilter.ALL, HomeSort.HEAT, page = 1, pageSize = 50, adultEnabled = false, adultDisplay = "badge")
        assertEquals("badge", params["adult"])
        assertFalse(params.containsKey("rating"))
    }

    @Test
    fun `FI badge 策略选中 18R 请求 adult=badge&rating=adult（只看 18R）`() {
        val params = HomeFilterParams.paramsFor(HomeFilter.ALL, HomeSort.HEAT, page = 1, pageSize = 50, adultEnabled = true, adultDisplay = "badge")
        assertEquals("badge", params["adult"])
        assertEquals("adult", params["rating"])
    }

    @Test
    fun `FI show 策略未选中 adult=show 不带 rating（成人按普通展示）`() {
        val params = HomeFilterParams.paramsFor(HomeFilter.ALL, HomeSort.HEAT, page = 1, pageSize = 50, adultEnabled = false, adultDisplay = "show")
        assertEquals("show", params["adult"])
        assertFalse(params.containsKey("rating"))
    }

    @Test
    fun `FI show 策略选中 18R 请求 adult=show&rating=adult（只看 18R 无角标）`() {
        val params = HomeFilterParams.paramsFor(HomeFilter.ALL, HomeSort.HEAT, page = 1, pageSize = 50, adultEnabled = true, adultDisplay = "show")
        assertEquals("show", params["adult"])
        assertEquals("adult", params["rating"])
    }
}
