package com.kheti.layout

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.sp
import com.kheti.AdjustmentPlanner
import com.kheti.KhetiMetrics
import com.kheti.KhetiRegion
import com.kheti.PlannedText
import com.kheti.VerticalGlyph
import com.kheti.VerticalOrientation
import com.kheti.digitRunOrientation
import com.kheti.latinRunOrientation
import kotlin.math.abs
import kotlin.math.roundToInt

/** 竖排时列内内容沿竖轴的对齐（对应上游 `--vertical` 下的 `text-align`）。 */
enum class KhetiVerticalAlignment { Top, Center, Bottom }

/** 一个已定位的绘制单元：朝向一致的一段文字。 */
class KhetiVRun(
    val start: Int,
    val end: Int,
    val orientation: VerticalOrientation,
    /** 沿竖轴的起点（相对列顶）。 */
    val y: Float,
    /** 沿竖轴占用的长度。 */
    val advance: Float,
    /** 横排字形挪到竖排位置所需的额外偏移（px，标点角位用）。 */
    val dx: Float,
    val dy: Float,
)

/**
 * 竖排注音的一个字符。
 *
 * 竖排中 ruby 位于基文**右侧**：本实现把它排进列与列之间的行间空白
 * （1.5 行高时恰有 0.5em 空隙，正好容纳 50% 字号的注音）。
 */
class KhetiVRubyGlyph(
    val ch: Char,
    val baseStart: Int,
    val baseEnd: Int,
    /** 相对列左边界的 x。 */
    val x: Float,
    /** 相对列顶的 y（与 [KhetiVRun.y] 同坐标系）。 */
    val y: Float,
    val fontSizePx: Float,
)

/** 一列（竖排的"行"）。 */
class KhetiVColumn(
    val index: Int,
    /** 列左边界。 */
    val x: Float,
    val top: Float,
    /** 内容沿竖轴的占用长度（**不含**悬挂标点）。 */
    val height: Float,
    val runs: List<KhetiVRun>,
    val charY: FloatArray,
    val charAdvance: FloatArray,
    /** 悬挂标点的字符下标（绝对值），-1 表示无。 */
    val hangingIndex: Int,
    /**
     * 实际绘制范围（**含**悬挂标点）。
     *
     * 画布高度必须用它，否则悬挂标点会落在画布之外被裁掉——
     * 这正是"竖排时末尾 `）` 看不见"的原因。
     */
    val drawnHeight: Float = height,
    /** 本列的行间注（竖排：基文右侧）。 */
    val ruby: List<KhetiVRubyGlyph> = emptyList(),
)

/** 竖排布局结果。 */
class KhetiVerticalLayout(
    val text: String,
    val columns: List<KhetiVColumn>,
    val size: IntSize,
    val plan: PlannedText,
    val columnWidthPx: Float,
    val fontSizePx: Float,
    val lineHeightPx: Float,
) {
    val columnCount: Int get() = columns.size

    /** 命中测试：返回最接近的字符下标。 */
    fun getOffsetForPosition(position: Offset): Int {
        if (columns.isEmpty()) return 0
        // 列按右→左排列：先挑列，再挑列内字符
        val col = columns.minByOrNull { abs(position.x - (it.x + columnWidthPx / 2f)) } ?: return 0
        val local = position.y - col.top
        var best = col.charY.indices.minByOrNull { abs(local - col.charY[it]) } ?: 0
        return col.runs.firstOrNull()?.start?.plus(best) ?: best
    }
}

/**
 * 竖排引擎（对应上游 `writing-mode: vertical-rl`）。
 *
 * 与横排引擎共用 `AdjustmentPlanner` 的增量：`margin-inline-*` 是**逻辑属性**，
 * 在竖排中作用于竖轴，因此标点挤压/中西文间距的数值完全一致（赫蹏正因使用逻辑属性而无缝支持竖排）。
 *
 * Compose 全平台无竖排 API，故字形朝向与标点角位由 [VerticalGlyph] 表驱动。
 */
class KhetiVerticalEngine(
    private val measurer: TextMeasurer,
    private val density: Density,
) {

    fun layout(
        text: String,
        style: KhetiTextStyle = KhetiTextStyle(),
        maxHeightPx: Int,
        alignment: KhetiVerticalAlignment = KhetiVerticalAlignment.Top,
        hang: KhetiHang = KhetiHang.LineEnd,
        spacing: Boolean = true,
        region: KhetiRegion = KhetiRegion.CN,
        /** 竖排字距：上游 `--vertical { letter-spacing: .125em }`。 */
        letterSpacingEm: Float = KhetiMetrics.LETTER_SPACING_VERTICAL_EM,
        /** 列宽（= 行高）。上游由 `line-height` 决定。 */
        columnWidthPx: Float = -1f,
        /** 行间注（下标基于 [text] 原文）；竖排时注音排在基文右侧。 */
        ruby: List<KhetiRuby> = emptyList(),
        rubyScale: Float = KhetiMetrics.RUBY_SCALE,
    ): KhetiVerticalLayout {
        val plan = if (spacing) AdjustmentPlanner.plan(text) else AdjustmentPlanner.identity(text)
        val s = plan.text
        val n = s.length
        val ts = style.toTextStyle()
        val fontSizePx = style.fontSizePx(density)
        val lineHeightPx = style.lineHeightPx(density)
        val colWidth = if (columnWidthPx > 0f) columnWidthPx else lineHeightPx

        if (n == 0) {
            return KhetiVerticalLayout("", emptyList(), IntSize(0, 0), plan, colWidth, fontSizePx, lineHeightPx)
        }

        // ---- 逐字增量（沿竖轴）----
        val inGap = BooleanArray(n)
        for (r in plan.gapRanges) for (i in r) if (i in 0 until n) inGap[i] = true
        val leadPx = FloatArray(n)
        val trailPx = FloatArray(n)
        for (i in 0 until n) {
            leadPx[i] = plan.leadingEm[i] * fontSizePx
            val ls = if (inGap[i]) 0f else letterSpacingEm
            trailPx[i] = (plan.trailingEm[i] + ls) * fontSizePx
        }

        // 行间注：原文下标 → 归一化下标（与横排同一套映射）
        val rubyPlanned = ruby.mapNotNull { rb ->
            if (n == 0) return@mapNotNull null
            val s0 = rb.start.coerceIn(0, text.length - 1)
            val e0 = (rb.end - 1).coerceIn(0, text.length - 1)
            val ps = plan.plannedIndex(s0)
            val pe = plan.plannedIndex(e0) + 1
            if (pe > ps && ps in 0 until n) RubySpan(ps, minOf(pe, n), rb.annotation) else null
        }

        // ---- 切分朝向一致的单元 ----
        val items = buildItems(s, ts, fontSizePx, region)
        val maxH = if (maxHeightPx in 1 until Int.MAX_VALUE) maxHeightPx else Int.MAX_VALUE

        // ---- 填列（\n 强制换列；超出高度也换列）----
        val columns = ArrayList<KhetiVColumn>()
        var itemIndex = 0
        while (itemIndex < items.size) {
            val colRuns = ArrayList<KhetiVRun>()
            var y = 0f
            var colStart = items[itemIndex].start
            var hardBreakAfter = false
            while (itemIndex < items.size) {
                val it = items[itemIndex]
                if (it.hardBreak) {
                    itemIndex++
                    hardBreakAfter = true
                    break
                }
                val lead = leadPx[it.start]
                val trail = trailPx[it.end - 1]
                val total = lead + it.advance + trail
                if (y + total > maxH && colRuns.isNotEmpty()) break
                colRuns += KhetiVRun(
                    start = it.start,
                    end = it.end,
                    orientation = it.orientation,
                    y = y + lead,
                    advance = it.advance,
                    dx = it.dx,
                    dy = it.dy,
                )
                y += total
                itemIndex++
            }
            if (colRuns.isEmpty()) continue

            // 悬挂：列末为可悬挂标点时不计其占位（竖排即"挂出列尾"）
            val drawnHeight = y // 含悬挂字形的真实绘制范围
            var hanging = -1
            var contentHeight = y
            val lastCharIndex = colRuns.last().end - 1
            if (hang == KhetiHang.LineEnd && KhetiMetrics.isHangable(s[lastCharIndex])) {
                val lastRun = colRuns.last()
                if (lastRun.end - lastRun.start == 1) {
                    hanging = lastCharIndex
                    contentHeight -= (lastRun.advance + trailPx[lastCharIndex])
                }
            }

            // 列内对齐
            val alignOffset = when (alignment) {
                KhetiVerticalAlignment.Top -> 0f
                KhetiVerticalAlignment.Center -> ((maxH.takeIf { it != Int.MAX_VALUE }?.toFloat() ?: contentHeight) - contentHeight) / 2f
                KhetiVerticalAlignment.Bottom -> (maxH.takeIf { it != Int.MAX_VALUE }?.toFloat() ?: contentHeight) - contentHeight
            }

            val colEnd = colRuns.last().end
            val charY = FloatArray(colEnd - colStart)
            val charAdv = FloatArray(colEnd - colStart)
            for (run in colRuns) {
                for (i in run.start until run.end) {
                    charY[i - colStart] = run.y + alignOffset
                    charAdv[i - colStart] = if (i == run.end - 1) run.advance else run.advance / (run.end - run.start)
                }
            }

            // 竖排行间注：注音排在基文**右侧**（列与列之间的行间空白里），
            // 逐字竖排并整体居中于基文区间的竖轴范围。
            val rubyGlyphs = if (rubyPlanned.isEmpty()) {
                emptyList()
            } else {
                val rubyFontSize = fontSizePx * rubyScale
                // 字身框右缘再留一点点缝
                val rubyX = (colWidth - fontSizePx) / 2f + fontSizePx + fontSizePx * 0.04f
                val out = ArrayList<KhetiVRubyGlyph>()
                for (rs in rubyPlanned) {
                    val bs = maxOf(rs.start, colStart)
                    val be = minOf(rs.end, colEnd)
                    if (be <= bs) continue
                    val covering = colRuns.filter { it.start < be && it.end > bs }
                    if (covering.isEmpty()) continue
                    val y0 = covering.minOf { it.y }
                    val y1 = covering.maxOf { it.y + it.advance }
                    val total = rs.annotation.length * rubyFontSize
                    var y = (y0 + y1) / 2f - total / 2f + alignOffset
                    for (ch in rs.annotation) {
                        out += KhetiVRubyGlyph(ch, bs, be, rubyX, y, rubyFontSize)
                        y += rubyFontSize
                    }
                }
                out
            }

            columns += KhetiVColumn(
                index = columns.size,
                x = 0f, // 列 x 在知道总列数后统一计算
                top = alignOffset,
                height = contentHeight,
                runs = colRuns,
                charY = charY,
                charAdvance = charAdv,
                hangingIndex = hanging,
                drawnHeight = drawnHeight,
                ruby = rubyGlyphs,
            )
            if (!hardBreakAfter && itemIndex >= items.size) break
        }

        // ---- 列序：右 → 左 ----
        // 有注音时最右列右侧还要留出注音宽度，否则它会落在画布之外被裁掉
        val rubyReserve = if (rubyPlanned.isEmpty()) 0f else fontSizePx * rubyScale
        val totalWidth = columns.size * colWidth + rubyReserve
        val placed = columns.mapIndexed { k, c ->
            KhetiVColumn(
                index = c.index,
                x = totalWidth - rubyReserve - (k + 1) * colWidth,
                top = c.top,
                height = c.height,
                runs = c.runs,
                charY = c.charY,
                charAdvance = c.charAdvance,
                hangingIndex = c.hangingIndex,
                drawnHeight = c.drawnHeight,
                ruby = c.ruby,
            )
        }
        // 画布高度必须按**含悬挂标点**的绘制范围取，否则挂出列尾的字形会被裁掉
        val maxContentH = placed.maxOfOrNull { it.top + it.drawnHeight } ?: 0f
        return KhetiVerticalLayout(
            text = s,
            columns = placed,
            size = IntSize(totalWidth.roundToInt(), maxContentH.roundToInt()),
            plan = plan,
            columnWidthPx = colWidth,
            fontSizePx = fontSizePx,
            lineHeightPx = lineHeightPx,
        )
    }

    // ------------------------------------------------------------------ 内部

    private class RubySpan(val start: Int, val end: Int, val annotation: String)

    private class Item(
        val start: Int,
        val end: Int,
        val orientation: VerticalOrientation,
        val advance: Float,
        val dx: Float,
        val dy: Float,
        val hardBreak: Boolean = false,
    )

    /** 把文本切成"朝向一致"的单元，并测出各自沿竖轴的占用长度。 */
    private fun buildItems(
        s: String,
        ts: TextStyle,
        fontSizePx: Float,
        region: KhetiRegion,
    ): List<Item> {
        val out = ArrayList<Item>()
        var i = 0
        while (i < s.length) {
            val ch = s[i]
            if (ch == '\n') {
                out += Item(i, i + 1, VerticalOrientation.Upright, 0f, 0f, 0f, hardBreak = true)
                i++
                continue
            }
            when {
                VerticalGlyph.isRotated(ch) -> {
                    var j = i
                    while (j < s.length && VerticalGlyph.isRotated(s[j])) j++
                    out += rotated(s, i, j, ts)
                    i = j
                }

                ch.isDigit() -> {
                    var j = i
                    while (j < s.length && s[j].isDigit()) j++
                    val ori = digitRunOrientation(j - i)
                    val w = measureWidth(s.substring(i, j), ts)
                    out += Item(i, j, ori, if (ori == VerticalOrientation.CombineUpright) fontSizePx else w, 0f, 0f)
                    i = j
                }

                ch in 'A'..'Z' || ch in 'a'..'z' -> {
                    var j = i
                    while (j < s.length && (s[j] in 'A'..'Z' || s[j] in 'a'..'z')) j++
                    val ori = latinRunOrientation(j - i)
                    val w = measureWidth(s.substring(i, j), ts)
                    out += Item(i, j, ori, w, 0f, 0f)
                    i = j
                }

                else -> {
                    val w = measureWidth(ch.toString(), ts)
                    // 标点的区域位移在绘制层按 region 施加；这里只记录朝向与占位
                    out += Item(i, i + 1, VerticalGlyph.defaultOrientation(ch), w, 0f, 0f)
                    i++
                }
            }
        }
        return out
    }

    private fun rotated(s: String, start: Int, end: Int, ts: TextStyle): Item =
        Item(start, end, VerticalOrientation.Sideways, measureWidth(s.substring(start, end), ts), 0f, 0f)

    private fun measureWidth(text: String, ts: TextStyle): Float =
        measurer.measure(AnnotatedString(text), ts, constraints = Constraints()).size.width.toFloat()
}

/**
 * 绘制竖排布局。
 *
 * - 正立字：把**墨迹盒**居中到字身格（汉字设计上填满全角框，故等价于常规竖排）。
 * - 标点：按 [VerticalGlyph.punctuationOffsetEm] 给出的区域规范把横排字形挪到角位。
 * - 旋转单元：绕枢轴旋转 90°（顺时针），文字自上游向下阅读。
 * - 縦中横：数字横排居中于一个字身格。
 */
fun DrawScope.drawKhetiVerticalText(
    measurer: TextMeasurer,
    layout: KhetiVerticalLayout,
    style: KhetiTextStyle,
    color: Color = Color.Unspecified,
    density: Density = Density(1f),
    region: KhetiRegion = KhetiRegion.CN,
    left: Float = 0f,
    top: Float = 0f,
) {
    if (layout.columns.isEmpty()) return
    val base = style.toTextStyle()
    val ts = if (color == Color.Unspecified) base else base.copy(color = color)
    val cell = ts.copy(lineHeight = style.fontSize)
    val fontSizePx = layout.fontSizePx
    val colWidth = layout.columnWidthPx
    val cellInset = (colWidth - fontSizePx) / 2f

    for (col in layout.columns) {
        val colX = left + col.x
        // 行间注：注音逐字排在基文右侧（竖排的 ruby 方向）
        for (g in col.ruby) {
            val s = g.ch.toString()
            val u = with(density) { g.fontSizePx.toSp() }
            val rs = ts.copy(fontSize = u, lineHeight = u)
            val mm = measurer.measure(AnnotatedString(s), rs)
            val box = mm.getBoundingBox(0)
            val x = colX + g.x + (g.fontSizePx - box.width) / 2f - box.left
            val y = top + col.top + g.y + (g.fontSizePx - box.height) / 2f - box.top
            drawText(measurer, s, topLeft = Offset(x, y), style = rs)
        }
        for (run in col.runs) {
            val text = layout.text.substring(run.start, run.end)
            val cellTop = top + col.top + run.y
            when (run.orientation) {
                VerticalOrientation.Sideways -> {
                    // 用**字身框样式**（行高 = 字号）而非整行样式：若用整行高（1.5em）的样式，
                    // 行距带来的空白会把旋转后的墨迹推离列中心，表现为括号与汉字不居中。
                    val px = colX + colWidth / 2f + fontSizePx / 2f
                    val py = cellTop
                    rotate(degrees = 90f, pivot = Offset(px, py)) {
                        drawText(measurer, text, topLeft = Offset(px, py), style = cell)
                    }
                }

                VerticalOrientation.CombineUpright -> {
                    // 縦中横必须塞进一个字身框；万一仍超宽则等比缩小（兜底）
                    val m0 = measurer.measure(AnnotatedString(text), cell)
                    val fit = if (m0.size.width > fontSizePx) fontSizePx / m0.size.width else 1f
                    val style2 = if (fit < 1f) {
                        cell.copy(fontSize = style.fontSize * fit, lineHeight = style.fontSize * fit)
                    } else {
                        cell
                    }
                    val m = measurer.measure(AnnotatedString(text), style2)
                    val x = colX + cellInset + (fontSizePx - m.size.width) / 2f
                    val y = cellTop + (run.advance - m.size.height) / 2f
                    drawText(measurer, text, topLeft = Offset(x, y), style = style2)
                }

                VerticalOrientation.Upright -> {
                    val single = text.length == 1
                    if (single && VerticalGlyph.isPunct(text[0])) {
                        // 标点：保持字身框对齐，再按区域规范位移（不按墨迹居中）
                        val (dxEm, dyEm) = VerticalGlyph.punctuationOffsetEm(text[0], region)
                        val x = colX + cellInset + dxEm * fontSizePx
                        val y = cellTop + dyEm * fontSizePx
                        drawText(measurer, text, topLeft = Offset(x, y), style = cell)
                    } else {
                        // 正立字：墨迹盒居中到字身格
                        val m = measurer.measure(AnnotatedString(text), cell)
                        val box = m.getBoundingBox(0)
                        val x = colX + cellInset + (fontSizePx - box.width) / 2f - box.left + run.dx
                        val y = cellTop + (run.advance - box.height) / 2f - box.top + run.dy
                        drawText(measurer, text, topLeft = Offset(x, y), style = cell)
                    }
                }
            }
        }
    }
}
