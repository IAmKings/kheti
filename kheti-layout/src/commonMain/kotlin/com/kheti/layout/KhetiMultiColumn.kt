package com.kheti.layout

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize

/**
 * D11 多栏排版（上游 `--columns-2/3/4` = CSS `column-count`，`--columns-Nem` = `column-width`）。
 *
 * 设计要点：**不新增排版引擎**。CSS 多栏本质是把一个超高单栏的**整行**分配到若干栏，
 * 因此直接复用现有 `KhetiLayout`：
 * 1. 先用 [KhetiEngine] 把文本排成"一栏到底"的 `KhetiLayout`；
 * 2. 按 `column-fill: balance` 的语义把**行**尽量均匀地分给 N 栏（行不可跨栏切分）；
 * 3. 每栏产出一个**子 `KhetiLayout`**（行 top 归零），绘制时按 (k × 栏宽) 平移即可，
 *    于是 ruby/行内效果/悬挂等全部自动继承。
 */
class KhetiMultiColumnLayout(
    /** 各栏子布局（行 top 已归零）。 */
    val columns: List<KhetiLayout>,
    /** 栏宽（px）。 */
    val columnWidthPx: Int,
    /** 栏间距（px）。 */
    val gapPx: Float,
    /** 整幅尺寸。 */
    val size: IntSize,
)

/** 与 `column-fill: balance` 等价的均衡分配：每栏行数差 ≤ 1。 */
internal fun balanceLines(total: Int, count: Int): List<Int> {
    if (count <= 0) return listOf(total)
    val base = total / count
    val extra = total % count
    return List(count) { if (it < extra) base + 1 else base }
}

/**
 * 把一栏到底的 [base] 切成 [count] 栏。
 *
 * @param count 栏数（上游 `--columns-N`；N ∈ 2..4）
 * @param gapPx 栏间距（上游未显式设 `column-gap`，由栏宽与容器宽决定；此处显式给出）
 */
fun splitIntoColumns(
    base: KhetiLayout,
    count: Int,
    gapPx: Float,
): KhetiMultiColumnLayout {
    require(count >= 1) { "栏数必须 ≥ 1" }
    val lines = base.lines
    if (count == 1 || lines.size <= 1) {
        return KhetiMultiColumnLayout(
            columns = listOf(base),
            columnWidthPx = base.size.width,
            gapPx = 0f,
            size = IntSize(base.size.width, base.size.height),
        )
    }

    // 行高在本引擎内是统一的（同一 KhetiLayout 一个 lineHeightPx）
    val lineH = base.lineHeightPx
    val per = balanceLines(lines.size, count)
    val maxLinesPerColumn = per.maxOrNull() ?: lines.size
    val columnHeight = (maxLinesPerColumn * lineH).toInt()

    val columns = ArrayList<KhetiLayout>(count)
    var cursor = 0
    for (k in 0 until count) {
        val n = per.getOrElse(k) { 0 }
        val slice = lines.subList(cursor, (cursor + n).coerceAtMost(lines.size))
        cursor += n
        if (slice.isEmpty()) continue
        // 行 top 归零：子布局从 0 开始，绘制时再按栏位平移
        val top0 = slice.first().top
        val rebased = slice.map { l ->
            KhetiLine(
                start = l.start,
                end = l.end,
                top = l.top - top0,
                width = l.width,
                x = l.x,
                segments = l.segments,
                charX = l.charX,
                charAdvance = l.charAdvance,
                hangingIndex = l.hangingIndex,
                ruby = l.ruby.map { g ->
                    KhetiRubyGlyph(
                        baseStart = g.baseStart,
                        baseEnd = g.baseEnd,
                        annotation = g.annotation,
                        x = g.x,
                        y = g.y - top0,
                        width = g.width,
                        fontSizePx = g.fontSizePx,
                    )
                },
            )
        }
        columns += KhetiLayout(
            text = base.text,
            lines = rebased,
            size = IntSize(base.size.width, (slice.size * lineH).toInt()),
            plan = base.plan,
            lineHeightPx = base.lineHeightPx,
            inline = base.inline,
        )
    }

    val totalGap = gapPx * (columns.size - 1).coerceAtLeast(0)
    return KhetiMultiColumnLayout(
        columns = columns,
        columnWidthPx = base.size.width,
        gapPx = gapPx,
        size = IntSize(
            width = (base.size.width * columns.size + totalGap).toInt(),
            height = columnHeight,
        ),
    )
}

/**
 * 按**栏宽**求栏数（上游 `--columns-Nem` = CSS `column-width`）。
 *
 * CSS 的 `column-count` 与 `column-width` 可同时给出，最终栏数取两者的约束；
 * 这里按"容器宽 ÷ 栏宽"向下取整，并至少 1 栏。
 */
fun columnCountForWidth(
    availableWidthPx: Int,
    columnWidthPx: Float,
    gapPx: Float,
): Int {
    if (columnWidthPx <= 0f) return 1
    val n = ((availableWidthPx + gapPx) / (columnWidthPx + gapPx)).toInt()
    return n.coerceAtLeast(1)
}

/** 绘制多栏：逐栏按栏位平移绘制（复用 [drawKhetiText]）。 */
fun DrawScope.drawKhetiMultiColumn(
    measurer: TextMeasurer,
    multi: KhetiMultiColumnLayout,
    style: KhetiTextStyle,
    color: androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color.Unspecified,
    density: Density = Density(1f),
    topOffset: Float = 0f,
) {
    for ((k, col) in multi.columns.withIndex()) {
        val dx = k * (multi.columnWidthPx + multi.gapPx)
        drawKhetiText(
            measurer = measurer,
            layout = col,
            style = style,
            color = color,
            density = density,
            leftOffset = dx,
            topOffset = topOffset,
        )
    }
}

/** 供调用方按栏高/栏宽测量用的辅助尺寸。 */
internal fun columnSliceSize(width: Int, height: Int): Size = Size(width.toFloat(), height.toFloat())
