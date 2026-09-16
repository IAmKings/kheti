package com.kheti.layout

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp

/**
 * 把 [KhetiLayout] 画到画布上。
 *
 * 关键点：绘制用的是**引擎算好的 x/y**，`drawText` 只负责把一段字形放到指定位置；
 * 因此几何与平台文本引擎的 letterSpacing/justify 行为无关，跨平台一致
 * （Phase 0 实测：整行绘制 vs 逐字分段绘制像素差异为 0）。
 */
fun DrawScope.drawKhetiText(
    measurer: TextMeasurer,
    layout: KhetiLayout,
    style: KhetiTextStyle,
    color: Color = Color.Unspecified,
    density: Density = Density(1f),
    /** 水平偏移（px）：用于把多个块叠放在同一张画布上。 */
    leftOffset: Float = 0f,
    /** 垂直偏移（px）：用于把多个块叠放在同一张画布上。 */
    topOffset: Float = 0f,
    /** 着重号（上游 `heti-em`）：这些区间内每个字下方加实心圆点。 */
    emphasis: List<IntRange> = emptyList(),
    emphasisColor: Color = Color.Unspecified,
) {
    if (layout.lines.isEmpty()) return
    val base = style.toTextStyle()
    val ts = if (color == Color.Unspecified) base else base.copy(color = color)

    // 行内元素：底色先画（在文字之下）
    layout.inline?.let {
        drawKhetiInlineDecorations(layout, it, color, leftOffset, topOffset, backgroundPass = true)
    }

    for (line in layout.lines) {
        for (seg in line.segments) {
            // 上标/下标/小号：按逐字符缩放换样式，并施加基线位移
            val sc = layout.inline?.scale?.getOrNull(seg.start) ?: 1f
            val shiftPx = (layout.inline?.shiftEm?.getOrNull(seg.start) ?: 0f) * style.fontSizePx(density)
            val segStyle = if (sc == 1f) {
                ts
            } else {
                ts.copy(fontSize = style.fontSize * sc, lineHeight = style.lineHeight * sc)
            }
            drawText(
                textMeasurer = measurer,
                text = layout.text.substring(seg.start, seg.end),
                topLeft = Offset(seg.x + leftOffset, line.top + topOffset + shiftPx),
                style = segStyle,
            )
        }
    }

    // 行间注：注音在基文区间上居中
    for (line in layout.lines) {
        for (rg in line.ruby) {
            // px → sp 必须按 density 换算（否则高密度设备上字号被放大 4 倍）
            val rubyUnit = with(density) { rg.fontSizePx.toSp() }
            val rubyStyle = ts.copy(fontSize = rubyUnit, lineHeight = rubyUnit)
            drawText(
                textMeasurer = measurer,
                text = rg.annotation,
                topLeft = Offset(rg.x + leftOffset, rg.y + topOffset),
                style = rubyStyle,
            )
        }
    }

    if (emphasis.isEmpty()) return
    val fontSizePx = style.fontSizePx(density)
    // 着重号直径约 0.14em（贴近 text-emphasis: filled circle 的观感）
    val radius = fontSizePx * 0.07f
    val dotColor = if (emphasisColor == Color.Unspecified) {
        if (color == Color.Unspecified) Color.Black else color
    } else {
        emphasisColor
    }
    for (range in emphasis) {
        for (i in range) {
            val line = layout.lines.firstOrNull { i in it.start until it.end } ?: continue
            val k = i - line.start
            val cx = leftOffset + line.x + line.charX[k] + line.charAdvance[k] / 2f
            val cy = topOffset + line.top + layout.lineHeightPx - radius * 2.4f
            drawCircle(color = dotColor, radius = radius, center = Offset(cx, cy))
        }
    }
}

/** 行内元素：下划线/删除线最后画（覆盖在文字之上）。 */
fun DrawScope.drawKhetiInlineLines(
    layout: KhetiLayout,
    textColor: Color = Color.Unspecified,
    leftOffset: Float = 0f,
    topOffset: Float = 0f,
) {
    layout.inline?.let {
        drawKhetiInlineDecorations(layout, it, textColor, leftOffset, topOffset, backgroundPass = false)
    }
}
