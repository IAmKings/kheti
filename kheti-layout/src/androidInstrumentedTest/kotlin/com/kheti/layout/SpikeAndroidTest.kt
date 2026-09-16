package com.kheti.layout

import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertTrue

/**
 * Phase 0 Android 端（Minikin / StaticLayout）证据。
 *
 * 必须在**真机/模拟器**上跑：Robolectric 不会使用真实 Minikin，量不出真实语义。
 * 运行：`./gradlew :kheti-layout:connectedDebugAndroidTest`
 */
@RunWith(AndroidJUnit4::class)
class SpikeAndroidTest {

    private fun measurer() = Spike.measurer(
        createFontFamilyResolver(ApplicationProvider.getApplicationContext())
    )

    @Test
    fun 标定Android端letterSpacing语义() {
        val m = measurer()
        val style = Spike.baseStyle()

        val mid = Spike.probeLetterSpacing(m, style)
        val edge = Spike.probeLineEdge(m, style)

        println("===== 平台：Android / Minikin =====")
        print(Spike.formatLetterSpacing(mid, "Spike A（行中 A[中]B）"))
        print(Spike.formatLetterSpacing(listOf(edge), "Spike A（行首 [中]A）"))

        // 记录事实即可：Android 会把 letterSpacing 四舍五入到整像素，
        // 因此 0.32px（= 0.02em @16px，赫蹏的全局字距）预期被抹成 0。
        val tiny = mid.first { it.requestedPx < 1f }
        println(">>> 亚像素请求 ${tiny.requestedPx}px 的实测整行Δ = ${tiny.totalDeltaPx}px（若为 0 则证实像素量化）")
    }

    @Test
    fun Android端逐字定位与分段自绘可行() {
        val m = measurer()
        val report = Spike.probeSegmentedDraw(m, Spike.baseStyle())
        print(report.summary())

        assertTrue(report.cursorMonotonic, "逐字光标位应单调不减")

        // 与桌面端同样的判据：无增量时逐字分段绘制应与整行绘制几乎逐像素一致。
        // 若 Android 上出现明显差异，则引擎必须改为"按调整点切段"而非"逐字绘制"。
        val totalPixels = 40 * 200
        assertTrue(
            report.wholeVsSegmentedDiffPixels < totalPixels / 100,
            "分段绘制与整行绘制差异过大：${report.wholeVsSegmentedDiffPixels} 像素",
        )

        assertTrue(
            report.inkWidthDeltaPx < 0,
            "施加挤压后墨迹宽度应收缩，实测 ${report.inkWidthDeltaPx}px（期望 ${report.expectedDeltaPx}px）",
        )
    }
}
