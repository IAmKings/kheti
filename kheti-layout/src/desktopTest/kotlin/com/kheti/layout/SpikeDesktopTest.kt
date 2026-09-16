package com.kheti.layout

import androidx.compose.ui.text.font.createFontFamilyResolver
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Phase 0 桌面端（Skia/Skiko）证据。
 *
 * 本测试**故意宽松**：它记录平台事实（如 letterSpacing 的首尾分配），
 * 而不是断言某个平台的特定数值——这些数字要写进 `docs/phase0-evidence.md`。
 */
class SpikeDesktopTest {

    private fun measurer() = Spike.measurer(createFontFamilyResolver())

    @Test
    fun 标定桌面端letterSpacing语义并输出证据() {
        val m = measurer()
        val style = Spike.baseStyle()

        val mid = Spike.probeLetterSpacing(m, style)
        val edge = Spike.probeLineEdge(m, style)

        println("===== 平台：Desktop/Skia（Skiko） =====")
        print(Spike.formatLetterSpacing(mid, "Spike A（行中 A[中]B）"))
        print(Spike.formatLetterSpacing(listOf(edge), "Spike A（行首 [中]A）"))

        // 事实性断言：请求 4px 时首尾增量之和应接近 4px（分配方式由证据文档记录）
        val r = mid.first { it.requestedPx == 4f }
        assertTrue(
            r.leadingShiftPx + r.trailingAdvancePx > 3.0f,
            "整行增量应接近请求值，实测 lead=${r.leadingShiftPx} trail=${r.trailingAdvancePx}",
        )
    }

    @Test
    fun 逐字定位与分段自绘可行() {
        val m = measurer()
        val style = Spike.baseStyle()
        val report = Spike.probeSegmentedDraw(m, style)
        print(report.summary())

        // 1) 光标位必须单调，且与该字包围盒宽度一致（允许亚像素误差）
        assertTrue(report.cursorMonotonic, "逐字光标位应单调不减")
        assertTrue(report.maxAdvanceErrorPx < 0.5f, "advance 与包围盒宽度误差过大：${report.maxAdvanceErrorPx}")

        // 2) 无增量时，逐字分段绘制应与整行绘制几乎逐像素一致。
        //    若此处出现大量差异，说明跨字 shaping/kerning 无法用逐字绘制复现，
        //    此时引擎需改为"按调整点切段绘制"而不是"逐字绘制"。
        val totalPixels = 40 * 200
        assertTrue(
            report.wholeVsSegmentedDiffPixels < totalPixels / 100,
            "分段绘制与整行绘制差异过大：${report.wholeVsSegmentedDiffPixels} 像素",
        )

        // 3) 标点挤压（−0.5em ≈ −8px）应体现为墨迹宽度收缩
        assertEquals("借指纸。《汉书", report.planText)
        assertTrue(report.expectedDeltaPx < 0f, "本草应含挤压负增量")
        assertTrue(
            report.inkWidthDeltaPx < 0,
            "施加挤压后墨迹宽度应收缩，实测 ${report.inkWidthDeltaPx}px",
        )
    }
}
