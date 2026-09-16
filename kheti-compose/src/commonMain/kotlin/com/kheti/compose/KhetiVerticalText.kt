package com.kheti.compose

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import com.kheti.KhetiMetrics
import com.kheti.KhetiRegion
import com.kheti.layout.KhetiHang
import com.kheti.layout.KhetiRuby
import com.kheti.layout.KhetiTextStyle
import com.kheti.layout.KhetiVerticalAlignment
import com.kheti.layout.KhetiVerticalEngine
import com.kheti.layout.drawKhetiVerticalText

/**
 * kheti 竖排文本（对应上游 `writing-mode: vertical-rl`）。
 *
 * - 列序**右 → 左**；`\n` 强制换列（诗词因此天然"一句一列"）。
 * - 标点按 [region] 规范就位（大陆靠角 / 台湾居中）；行末标点自动悬挂到列尾之外。
 * - 汉字正立、拉丁词与括号旋转 90°、2~3 位数字作縦中横。
 * - **需要高度约束**（竖排沿高度排布）：请放在有确定高度的容器里。
 *
 * ⚠️ 展示型：不支持 IME 编辑。
 */
@Composable
fun KhetiVerticalText(
    text: String,
    modifier: Modifier = Modifier,
    size: KhetiMetrics.Size = KhetiMetrics.Size.XLarge,
    flavor: KhetiFlavor = KhetiFlavor.Classic,
    region: KhetiRegion = KhetiRegion.CN,
    hang: KhetiHang = KhetiHang.LineEnd,
    alignment: KhetiVerticalAlignment = KhetiVerticalAlignment.Top,
    spacing: Boolean = true,
    /** 行间注（下标基于 [text] 原文）：竖排时注音排在基文**右侧**。 */
    ruby: List<KhetiRuby> = emptyList(),
    rubyScale: Float = KhetiMetrics.RUBY_SCALE,
    color: Color = Color.Unspecified,
) {
    val density = LocalDensity.current
    val fonts = LocalKhetiFontFamilies.current
    val colors = LocalKhetiColors.current
    val style = remember(size, flavor, fonts) { KhetiTextStyle.body(size, flavor, fonts) }
    val resolvedColor = if (color == Color.Unspecified) colors.ink else color
    val measurer = rememberTextMeasurer()
    val engine = remember(measurer, density) { KhetiVerticalEngine(measurer, density) }
    val showGrid = LocalKhetiShowGrid.current

    BoxWithConstraints(modifier) {
        // 竖排必须有高度约束：无界时退化按内容估算，避免除零/无限
        val maxHeightPx = if (constraints.hasBoundedHeight) {
            constraints.maxHeight
        } else {
            val perChar = with(density) { style.fontSize.toPx() } * 1.15f
            (perChar * text.length).toInt().coerceAtMost(2000)
        }
        val limit = maxHeightPx.coerceAtLeast(1)

        val layout = remember(text, style, limit, region, hang, alignment, spacing, ruby, rubyScale) {
            engine.layout(
                text = text,
                style = style,
                maxHeightPx = limit,
                alignment = alignment,
                hang = hang,
                spacing = spacing,
                region = region,
                ruby = ruby,
                rubyScale = rubyScale,
            )
        }

        Canvas(
            Modifier
                .layout { measurable, _ ->
                    val w = layout.size.width.coerceAtLeast(1)
                    val h = layout.size.height.coerceAtLeast(1)
                    val placeable = measurable.measure(
                        Constraints(minWidth = w, maxWidth = w, minHeight = h, maxHeight = h)
                    )
                    layout(w, h) { placeable.placeRelative(0, 0) }
                }
                .semantics { this.text = AnnotatedString(text) },
        ) {
            if (showGrid) {
                // 竖排网格是**纵向**的：按布局自身的列盒边界画竖线（列宽 = 行高）
                val rule = Color.Gray.copy(alpha = 0.35f)
                val h = layout.size.height.toFloat()
                for (col in layout.columns) {
                    drawLine(rule, Offset(col.x, 0f), Offset(col.x, h), strokeWidth = 1f)
                }
                drawLine(rule, Offset(layout.size.width.toFloat(), 0f), Offset(layout.size.width.toFloat(), h), strokeWidth = 1f)
            }
            drawKhetiVerticalText(
                measurer = measurer,
                layout = layout,
                style = style,
                color = resolvedColor,
                density = density,
                region = region,
            )
        }
    }
}
