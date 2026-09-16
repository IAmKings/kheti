package com.kheti

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * D13 收尾：`q` 自动加引号。
 *
 * 上游关键行为：引号集合随**规范**（cn/tw/common）与**书写方向**（横排/竖排）变化；
 * 嵌套取层级（主→次→主）。
 */
class KhetiQuotesTest {

    @Test
    fun 默认规范用直角引号() {
        val q = wrapQuotes("他说打工是不可能打工的", listOf(2..11))
        assertEquals("他说「打工是不可能打工的」", q.text)
    }

    @Test
    fun 国标规范用弯引号() {
        val q = wrapQuotes("他说打工是不可能打工的", listOf(2..11), policy = QuotePolicy.CN)
        assertEquals("他说“打工是不可能打工的”", q.text)
    }

    @Test
    fun 竖排改用竖排引号集合() {
        // 上游 --vertical 下 q 使用 vertical 集合（默认 common → 直角引号）
        val horizontal = wrapQuotes("天地玄黄", listOf(0..3), policy = QuotePolicy.CN, flow = KhetiFlow.Horizontal)
        assertEquals("“天地玄黄”", horizontal.text)
        val vertical = wrapQuotes("天地玄黄", listOf(0..3), policy = QuotePolicy.CN, flow = KhetiFlow.Vertical)
        assertEquals("『天地玄黄』", vertical.text, "竖排应换成竖排引号集合")
    }

    @Test
    fun 嵌套按层级取主次引号() {
        // 他说引用完毕 → 外层 2..5（引用完毕），内层 3..4（用完）
        val q = wrapQuotes("他说引用完毕", listOf(2..5, 3..4))
        assertEquals("他说「引『用完』毕」", q.text)
    }

    @Test
    fun 下标映射可用于后续区间() {
        val text = "abc中文def"
        val he = text.indexOf('中')
        val q = wrapQuotes(text, listOf(0..2))
        // 原文 [0,2] 前后各插入一个引号 → 其后所有下标 +2
        assertEquals("「abc」中文def", q.text)
        assertEquals(he + 2, q.mapIndex(he), "后续区间应能映射到新文本下标")
        assertEquals('中', q.text[q.mapIndex(he)], "映射后必须仍指向同一个字")
    }
}
