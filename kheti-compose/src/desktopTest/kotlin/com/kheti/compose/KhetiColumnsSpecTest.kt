package com.kheti.compose

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * D11 多栏公共 API 的规格计算（纯函数部分）。
 *
 * 两种上游模式：
 * - `--columns-N`：直接给栏数
 * - `--columns-Nem`：给栏宽，栏数由可用宽反推
 */
class KhetiColumnsSpecTest {

    @Test
    fun 按栏数分栏时栏宽为可用宽扣除间距后均分() {
        // 可用 800px、2 栏、间距 20 → 每栏 (800-20)/2 = 390
        val spec = khetiColumnsSpec(availableWidthPx = 800, count = 2, gapPx = 20f)
        assertEquals(2, spec.count)
        assertEquals(390, spec.columnWidthPx)
        assertEquals(800, spec.totalWidthPx, "栏宽 × 栏数 + 间距 应回到可用宽")
    }

    @Test
    fun 按栏宽分栏时栏数由可用宽反推() {
        // 可用 800、栏宽 200、间距 20 → floor((800+20)/(200+20)) = 3 栏
        val spec = khetiColumnsSpec(availableWidthPx = 800, count = null, columnWidthPx = 200f, gapPx = 20f)
        assertEquals(3, spec.count)
        // 三栏均分： (800 - 40)/3 = 253
        assertEquals(253, spec.columnWidthPx)
    }

    @Test
    fun 放不下时退化为单栏() {
        val spec = khetiColumnsSpec(availableWidthPx = 150, count = null, columnWidthPx = 200f, gapPx = 20f)
        assertEquals(1, spec.count)
        assertEquals(150, spec.columnWidthPx)
        assertTrue(spec.totalWidthPx <= 150)
    }

    @Test
    fun 缺省与非法输入均退化为单栏() {
        assertEquals(1, khetiColumnsSpec(600).count)
        assertEquals(1, khetiColumnsSpec(600, count = 0).count)
        assertEquals(1, khetiColumnsSpec(600, count = null, columnWidthPx = 0f).count)
    }
}
