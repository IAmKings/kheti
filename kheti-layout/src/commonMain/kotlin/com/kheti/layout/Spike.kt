package com.kheti.layout

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import com.kheti.AdjustmentPlanner
import kotlin.math.abs

/**
 * Phase 0 证据探针（临时代码，Phase 4 前删除）。
 *
 * 用**实测数字**回答两个决定架构的问题：
 *  A. `SpanStyle.letterSpacing` 在各平台如何分配首/尾增量、是否被像素量化
 *     → 决定能否用它冒充赫蹏的 `margin-inline-end`；
 *  B. 「整行度量 → 逐字定位 → 分段自绘」能否复现整行绘制
 *     → 决定主引擎（方案 B）可行性。
 *
 * 坐标一律使用 Float，避免整数量化掩盖真实误差。
 */
object Spike {

    /**
     * 平台无关的度量器工厂：解析器由各平台提供
     * （desktop 用无参 `createFontFamilyResolver()`，Android 需 `Context`）。
     */
    fun measurer(resolver: FontFamily.Resolver): TextMeasurer = TextMeasurer(
        defaultFontFamilyResolver = resolver,
        defaultDensity = Density(1f),
        defaultLayoutDirection = LayoutDirection.Ltr,
    )

    /** 与赫蹏默认一致的基准样式：16px / 1.5 行高。 */
    fun baseStyle(): TextStyle = TextStyle(fontSize = 16.sp, lineHeight = 24.sp)

    // ---------------------------------------------------------------- Spike A

    data class LetterSpacingRow(
        val requestedPx: Float,
        val leadingShiftPx: Float,
        val trailingAdvancePx: Float,
        val totalDeltaPx: Float,
    )

    /**
     * 在「A中B」上给中间那个字加 `letterSpacing`，拆出首/尾增量：
     * - leadingShift = 该字自身光标位位移
     * - trailingAdvance = 后一个字光标位位移 − leadingShift
     * - totalDelta = 整行宽度变化
     *
     * 赫蹏需要「单侧 +0.25em」。若平台给出 leading≈trailing≈X/2，则 `letterSpacing`
     * **无法**表达单侧边距——这正是本探针要证明或否证的。
     */
    fun probeLetterSpacing(
        measurer: TextMeasurer,
        style: TextStyle,
        requestedPxList: List<Float> = listOf(4f, 0.32f, -4f),
    ): List<LetterSpacingRow> {
        val text = "A中B"
        val plain = measurer.measure(AnnotatedString(text), style)
        val p1 = plain.getHorizontalPosition(1, true)
        val p2 = plain.getHorizontalPosition(2, true)
        // 用浮点光标位算总宽：IntSize.width 会把亚像素差抹掉，掩盖真实语义
        val w0 = plain.getHorizontalPosition(3, true)

        return requestedPxList.map { px ->
            val styled = measurer.measure(
                buildAnnotatedString {
                    append("A")
                    withStyle(SpanStyle(letterSpacing = px.sp)) { append("中") }
                    append("B")
                },
                style,
            )
            val q1 = styled.getHorizontalPosition(1, true)
            val q2 = styled.getHorizontalPosition(2, true)
            val leading = q1 - p1
            val trailing = (q2 - p2) - leading
            val total = styled.getHorizontalPosition(3, true) - w0
            LetterSpacingRow(px, leading, trailing, total)
        }
    }

    /** 行首字符加 letterSpacing：观察平台是否在行首剥离左半增量。 */
    fun probeLineEdge(
        measurer: TextMeasurer,
        style: TextStyle,
        px: Float = 4f,
    ): LetterSpacingRow {
        val text = "中A"
        val plain = measurer.measure(AnnotatedString(text), style)
        val p0 = plain.getHorizontalPosition(0, true)
        val p1 = plain.getHorizontalPosition(1, true)
        val styled = measurer.measure(
            buildAnnotatedString {
                withStyle(SpanStyle(letterSpacing = px.sp)) { append("中") }
                append("A")
            },
            style,
        )
        val q0 = styled.getHorizontalPosition(0, true)
        val q1 = styled.getHorizontalPosition(1, true)
        val leading = q0 - p0
        val trailing = (q1 - p1) - leading
        return LetterSpacingRow(
            px,
            leading,
            trailing,
            styled.getHorizontalPosition(2, true) - plain.getHorizontalPosition(2, true),
        )
    }

    fun formatLetterSpacing(rows: List<LetterSpacingRow>, title: String): String = buildString {
        appendLine("$title（请求值 → 首位移 / 尾增量 / 整行Δ，单位 px）")
        for (r in rows) {
            appendLine(
                "  %8.3f → lead %8.3f | trail %8.3f | total %8.3f".format(
                    r.requestedPx, r.leadingShiftPx, r.trailingAdvancePx, r.totalDeltaPx
                )
            )
        }
    }

    // ---------------------------------------------------------------- Spike B

    data class SegmentedDrawReport(
        val charCount: Int,
        val cursorMonotonic: Boolean,
        val maxAdvanceErrorPx: Float,
        val wholeVsSegmentedDiffPixels: Int,
        val inkWidthPlainPx: Int,
        val inkWidthAdjustedPx: Int,
        val inkWidthDeltaPx: Int,
        val expectedDeltaPx: Float,
        val planText: String,
        val adjustmentCount: Int,
    ) {
        fun summary(): String = buildString {
            appendLine("Spike B — 整行度量 → 逐字定位 → 分段自绘（文本：$planText）")
            appendLine("  字符数=$charCount  光标位单调=$cursorMonotonic  最大 advance 误差=${"%.4f".format(maxAdvanceErrorPx)}px")
            appendLine("  整行绘制 vs 逐字分段绘制：像素差异=$wholeVsSegmentedDiffPixels")
            appendLine("  墨迹宽度：未调整=${inkWidthPlainPx}px  调整后=${inkWidthAdjustedPx}px  差=${inkWidthDeltaPx}px  期望=${"%.2f".format(expectedDeltaPx)}px")
            appendLine("  规划出的调整区间数=$adjustmentCount")
        }
    }

    fun probeSegmentedDraw(
        measurer: TextMeasurer,
        style: TextStyle,
        text: String = "借指纸。《汉书",
        fontSizePx: Float = 16f,
    ): SegmentedDrawReport {
        val layout: TextLayoutResult = measurer.measure(AnnotatedString(text), style)
        val xs = (0..text.length).map { layout.getHorizontalPosition(it, true) }

        var maxErr = 0f
        var monotonic = true
        for (i in 0 until text.length) {
            val adv = xs[i + 1] - xs[i]
            if (adv < -0.001f) monotonic = false
            maxErr = maxOf(maxErr, abs(adv - layout.getBoundingBox(i).width))
        }

        val plan = AdjustmentPlanner.plan(text)
        val height = 40
        val width = (layout.size.width + 64f).toInt().coerceAtLeast(96)

        // (a) 整行一次绘制
        val whole = renderWhole(measurer, style, plan.text, height, width)
        // (b) 逐字分段绘制（增量全为 0 的基准位置）
        val segmented = renderPerChar(measurer, style, plan.text, xs, height, width)
        val diff = diffPixels(whole, segmented)

        // (c) 施加赫蹏增量后重绘
        val adjustedXs = adjustedPositions(plan.leadingEm, plan.trailingEm, xs, fontSizePx)
        val adjusted = renderPerChar(measurer, style, plan.text, adjustedXs, height, width)

        val inkPlain = inkWidth(whole, width, height)
        val inkAdjusted = inkWidth(adjusted, width, height)

        return SegmentedDrawReport(
            charCount = text.length,
            cursorMonotonic = monotonic,
            maxAdvanceErrorPx = maxErr,
            wholeVsSegmentedDiffPixels = diff,
            inkWidthPlainPx = inkPlain,
            inkWidthAdjustedPx = inkAdjusted,
            inkWidthDeltaPx = inkAdjusted - inkPlain,
            expectedDeltaPx = plan.totalDeltaEm() * fontSizePx,
            planText = plan.text,
            adjustmentCount = plan.spans().size,
        )
    }

    /** 按逐字增量把光标位推进为"调整后位置"（浮点，不量化）。 */
    fun adjustedPositions(
        leadingEm: List<Float>,
        trailingEm: List<Float>,
        baseXs: List<Float>,
        fontSizePx: Float,
    ): List<Float> {
        val out = ArrayList<Float>(leadingEm.size + 1)
        var shift = 0f
        for (i in leadingEm.indices) {
            shift += leadingEm[i] * fontSizePx
            out.add(baseXs[i] + shift)
            shift += trailingEm[i] * fontSizePx
        }
        out.add(baseXs[leadingEm.size] + shift)
        return out
    }

    private fun renderWhole(
        measurer: TextMeasurer,
        style: TextStyle,
        text: String,
        height: Int,
        width: Int,
    ): ImageBitmap = render(height, width) {
        drawText(measurer, text, topLeft = Offset(0f, 8f), style = style)
    }

    private fun renderPerChar(
        measurer: TextMeasurer,
        style: TextStyle,
        text: String,
        xs: List<Float>,
        height: Int,
        width: Int,
    ): ImageBitmap = render(height, width) {
        val limit = minOf(text.length, xs.size - 1)
        for (i in 0 until limit) {
            drawText(
                textMeasurer = measurer,
                text = text.substring(i, i + 1),
                topLeft = Offset(xs[i], 8f),
                style = style,
            )
        }
    }

    private fun render(
        height: Int,
        width: Int,
        block: androidx.compose.ui.graphics.drawscope.DrawScope.() -> Unit,
    ): ImageBitmap {
        val bitmap = ImageBitmap(width, height)
        val canvas = Canvas(bitmap)
        CanvasDrawScope().draw(
            Density(1f),
            LayoutDirection.Ltr,
            canvas,
            Size(width.toFloat(), height.toFloat()),
            block,
        )
        return bitmap
    }

    private fun diffPixels(a: ImageBitmap, b: ImageBitmap): Int {
        val pa = a.toPixelMap()
        val pb = b.toPixelMap()
        var n = 0
        for (y in 0 until a.height) {
            for (x in 0 until a.width) {
                if (pa[x, y] != pb[x, y]) n++
            }
        }
        return n
    }

    private fun inkWidth(bitmap: ImageBitmap, width: Int, height: Int): Int {
        val p = bitmap.toPixelMap()
        var min = Int.MAX_VALUE
        var max = Int.MIN_VALUE
        for (y in 0 until height) {
            for (x in 0 until width) {
                if (p[x, y].alpha > 0.02f) {
                    if (x < min) min = x
                    if (x > max) max = x
                }
            }
        }
        return if (max < min) 0 else max - min + 1
    }
}
