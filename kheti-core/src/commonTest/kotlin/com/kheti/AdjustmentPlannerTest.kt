package com.kheti

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 规则层单测。
 *
 * 断言的是**赫蹏的行为**，不是"看起来合理"：期望值取自上游 `js/heti-addon.js` 的规则
 * 与 `_site/index.html` 的官方示例（如 `。《`、`》：「` 均标注为挤压对）。
 */
class AdjustmentPlannerTest {

    private fun plan(s: String) = AdjustmentPlanner.plan(s)

    @Test
    fun 纯中文不加任何间距() {
        val p = plan("李白乘舟将欲行")
        assertEquals("李白乘舟将欲行", p.text)
        assertTrue(p.leadingEm.all { it == 0f })
        assertTrue(p.trailingEm.all { it == 0f })
        assertEquals(0f, p.totalDeltaEm())
    }

    @Test
    fun 行内行末标点不挤压() {
        // 逗号/句号在行末、后面没有括号类，不应触发挤压（上游需 next 属于特定集合）
        val p = plan("独在异乡为异客，")
        assertEquals(0f, p.trailingEm.last(), "行末逗号不应被挤压")
    }

    @Test
    fun 停顿号接开括号挤压半字宽() {
        // 官方示例标注的挤压对：。《 以及 》：「
        val p = plan("借指纸。《汉书·外戚传")
        val idx = p.text.indexOf('。')
        assertEquals(-0.5f, p.trailingEm[idx], "。《 应为半字宽挤压")

        val p2 = plan("赵皇后》：「武发篋中")
        val close = p2.text.indexOf('》')
        assertEquals(-0.5f, p2.trailingEm[close], "》：「 中 》 应为半字宽挤压")
    }

    @Test
    fun 开括号连排挤压半字宽() {
        val p = plan("《〈【")
        assertEquals(-0.5f, p.trailingEm[0])
        assertEquals(-0.5f, p.trailingEm[1])
        assertEquals(0f, p.trailingEm[2], "末字无后继不应挤压")
    }

    @Test
    fun 间隔符挤压四分之一字宽() {
        // 注意：只有 U+2027（‧）是"纯间隔符"。U+00B7（·）落在赫蹏的 A 区间 \u0080-\u00ff，
        // 会被当作西文处理（见下一个测试），U+30FB（・）落在 CJK 区间。这里用 U+2027 才能
        // 单独触发 quarter 挤压规则。
        val p = plan("字‧《作品")
        assertEquals(-0.25f, p.trailingEm[p.text.indexOf('\u2027')], "‧ 接开括号应为 ¼ 字宽")
        assertEquals(0f, p.leadingEm[p.text.indexOf('\u2027')], "不应额外加中西文间距")
    }

    @Test
    fun 中点U00B7属于拉丁补充区因而走中西文间距而非挤压() {
        // 这是赫蹏的真实行为，容易被误认为 bug：U+00B7 在 A 区间内 → 触发 heti-spacing-end
        // （首侧 ¼ em），同时因为它已被 spacing 包裹，挤压趟次会跳过它。
        val p = plan("窃·《格瓦拉")
        val dot = p.text.indexOf('·')
        assertEquals(0.25f, p.leadingEm[dot], "· 前应补 ¼ 字宽（中西文间距）")
        assertEquals(0f, p.trailingEm[dot], "· 不应再被挤压（已被 spacing 包裹）")
    }

    @Test
    fun 弯引号特例挤压四分之一字宽() {
        val stopQuote = plan("他说。“好”")
        assertEquals(-0.25f, stopQuote.trailingEm[stopQuote.text.indexOf('。')])

        val openQuote = plan("“《赵后传》")
        assertEquals(-0.25f, openQuote.trailingEm[openQuote.text.indexOf('“')])
    }

    @Test
    fun 中西文两侧各加四分之一字宽并吞掉手敲空格() {
        val p = plan("中文 abc 中文")
        assertEquals("中文abc中文", p.text, "中西文之间的手敲空格应被吞掉")
        assertEquals(2, p.removedSpaces)
        val a = p.text.indexOf('a')
        val c = p.text.indexOf('c')
        assertEquals(0.25f, p.leadingEm[a])
        assertEquals(0.25f, p.trailingEm[c])
    }

    @Test
    fun 行首西文接中文只加尾侧() {
        val p = plan("abc 中文")
        assertEquals("abc中文", p.text)
        assertEquals(0f, p.leadingEm[0])
        assertEquals(0.25f, p.trailingEm[2])
    }

    @Test
    fun 行尾中文接西文只加首侧() {
        val p = plan("中文 abc")
        assertEquals("中文abc", p.text)
        assertEquals(0.25f, p.leadingEm[2])
        assertEquals(0f, p.trailingEm.last())
    }

    @Test
    fun 西文串内部空格保留() {
        // 上游正则 (?: +[ANS]+)* 保留西文串内部空格，只修剪贴 CJK 的两端
        val p = plan("中文 Hello, world! 中文")
        assertEquals("中文Hello, world!中文", p.text)
        assertEquals(0.25f, p.leadingEm[p.text.indexOf('H')])
        assertEquals(0.25f, p.trailingEm[p.text.indexOf('!')])
    }

    @Test
    fun 幂等_对归一化文本重复规划不叠加() {
        val once = plan("中文 abc 中文")
        val twice = plan(once.text)
        assertEquals(once.text, twice.text)
        assertEquals(once.leadingEm, twice.leadingEm)
        assertEquals(once.trailingEm, twice.trailingEm)
    }

    @Test
    fun 单字挤压不超过半字宽() {
        // 任何字符的合法增量只有 {0, ±0.25, -0.5}
        val samples = listOf(
            "借指纸。《汉书》：「武」发篋中，有裹药二枚，赫蹏书。」",
            "Hello, world!是大家第一次学习Programming时最常写的demo",
            "窃·格瓦拉说：“《赵后传》所谓『赫蹏』者”",
        )
        for (s in samples) {
            val p = plan(s)
            for (v in p.leadingEm + p.trailingEm) {
                assertTrue(
                    v == 0f || v == 0.25f || v == -0.25f || v == -0.5f,
                    "非法增量 $v（样本：$s）",
                )
            }
        }
    }

    @Test
    fun spans合并后与逐字增量一致() {
        val p = plan("中文 abc 中文。《汉书")
        for (span in p.spans()) {
            for (i in span.start..span.end) {
                assertEquals(span.leadingEm, p.leadingEm[i])
                assertEquals(span.trailingEm, p.trailingEm[i])
            }
        }
    }
}
