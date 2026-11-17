package cn.yyliucha.steamdeals

import cn.yyliucha.steamdeals.data.api.NameListSerializer
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * CM-1 单测：开发商/发行商名称解析。
 * 夹具 = 生产真实报文（2026-11-17 GET https://sd.yyliucha.cn/api/app/games/916440 与 /105600
 * 的 developers/publishers 字段原文），不写假数据。
 * 反向自检：把 NameListSerializer.nameOf 里「JSON 对象文本 → name」的分支删掉（原样返回字符串）→
 * 用例 1 与「混合形态」用例必须红；把对象分支删掉 → 用例 3 红。
 */
class NameListSerializerTest {

    private fun parse(raw: String): List<String> =
        NameListSerializer.namesFrom(Json.parseToJsonElement(raw))

    @Test
    fun `生产纪元1800 对象文本元素取 name`() {
        // 守的约束：["{\"name\":\"Ubisoft Mainz\"}"] → ["Ubisoft Mainz"]，页面不再显示原始 JSON
        assertEquals(listOf("Ubisoft Mainz"), parse("""["{\"name\":\"Ubisoft Mainz\"}"]"""))
        assertEquals(
            listOf("Ubisoft"),
            parse("""["{\"name\":\"Ubisoft\",\"creator_clan_account_id\":33075774}"]"""),
        )
    }

    @Test
    fun `泰拉瑞亚纯字符串数组不回归`() {
        // 守的约束：["Re-Logic"] 原样返回（不解析、不改写）
        assertEquals(listOf("Re-Logic"), parse("""["Re-Logic"]"""))
    }

    @Test
    fun `对象数组契约形态取 name`() {
        // 守的约束：契约形态 [{name, creator_clan_account_id}] → 只取 name
        assertEquals(
            listOf("X", "Y"),
            parse("""[{"name":"X","creator_clan_account_id":1},{"name":"Y"}]"""),
        )
    }

    @Test
    fun `单对象与混合形态兜底`() {
        assertEquals(listOf("A"), parse("""{"name":"A"}"""))
        assertEquals(listOf("B", "C"), parse("""["B",{"name":"C"}]"""))
    }

    @Test
    fun `空值与非法形态过滤`() {
        assertEquals(emptyList<String>(), parse("""[]"""))
        assertEquals(emptyList<String>(), parse("""[null,"","   ",123]"""))
        // 对象文本解析不出 name → 丢弃而不是显示垃圾
        assertEquals(emptyList<String>(), parse("""["{\"x\":1}"]"""))
    }
}
