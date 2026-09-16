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
import com.kheti.layout.KhetiTextStyle
import com.kheti.layout.KhetiVerticalBlock
import com.kheti.layout.KhetiVerticalBlockSpec
import com.kheti.layout.KhetiVerticalEngine
import com.kheti.layout.drawKhetiVerticalText
import com.kheti.layout.layoutKhetiVerticalBlocks

/**
 * 竖排诗词：与横排 [KhetiPoem] 保持同一套层级 ——
 * 标题**楷体 + 更大字号 + 加粗**、元信息**小一号 + 次级颜色**、正文宋体。
 *
 * 块按**右 → 左**排列（标题在最右），与竖排阅读顺序一致。
 * 需要高度约束（竖排沿高度排布）。
 */
@Composable
fun KhetiVerticalPoem(
    stanzas: List<List<String>>,
    modifier: Modifier = Modifier,
    title: String? = null,
    meta: String? = null,
    flavor: KhetiFlavor = KhetiFlavor.Classic,
    size: KhetiMetrics.Size = KhetiMetrics.Size.XLarge,
    hang: KhetiHang = KhetiHang.LineEnd,
    region: KhetiRegion = KhetiRegion.CN,
) {
    val density = LocalDensity.current
    val fonts = LocalKhetiFontFamilies.current
    val colors = LocalKhetiColors.current
    val measurer = rememberTextMeasurer()
    val engine = remember(measurer, density) { KhetiVerticalEngine(measurer, density) }
    val showGrid = LocalKhetiShowGrid.current

    val specs = remember(stanzas, title, meta, flavor, size, fonts, colors) {
        buildKhetiVerticalPoemSpecs(stanzas, title, meta, flavor, size, fonts, colors)
    }

    BoxWithConstraints(modifier) {
        val maxHeightPx = if (constraints.hasBoundedHeight) {
            constraints.maxHeight
        } else {
            val perChar = with(density) { KhetiTextStyle.body(size, flavor, fonts).fontSize.toPx() } * 1.15f
            (perChar * 12).toInt().coerceAtMost(2000)
        }
        val limit = maxHeightPx.coerceAtLeast(1)
        val blockLayout = remember(specs, limit, region, hang) {
            layoutKhetiVerticalBlocks(engine, specs, limit, region, hang)
        }

        Canvas(
            Modifier
                .layout { measurable, _ ->
                    val w = blockLayout.width.coerceAtLeast(1)
                    val h = blockLayout.height.coerceAtLeast(1)
                    val placeable = measurable.measure(
                        Constraints(minWidth = w, maxWidth = w, minHeight = h, maxHeight = h)
                    )
                    layout(w, h) { placeable.placeRelative(0, 0) }
                }
                .semantics { this.text = AnnotatedString(semanticPoemText(title, meta, stanzas)) },
        ) {
            if (showGrid) {
                val rule = Color.Gray.copy(alpha = 0.35f)
                for (b in blockLayout.blocks) {
                    val h = b.layout.size.height.toFloat()
                    for (col in b.layout.columns) {
                        val x = b.x + col.x
                        drawLine(rule, Offset(x, 0f), Offset(x, h), strokeWidth = 1f)
                    }
                    val right = b.x + b.layout.size.width
                    drawLine(rule, Offset(right, 0f), Offset(right, h), strokeWidth = 1f)
                }
            }
            for (b in blockLayout.blocks) {
                drawKhetiVerticalText(
                    measurer = measurer,
                    layout = b.layout,
                    style = b.style,
                    color = b.color,
                    density = density,
                    region = region,
                    left = b.x,
                )
            }
        }
    }
}

/**
 * 构造竖排诗词的块规格（纯函数，便于测试）：
 * 标题 = 楷体 + H2(24px) + 800；元信息 = 小一号 + 次级色；正文 = 宋体 + 正文字号。
 */
fun buildKhetiVerticalPoemSpecs(
    stanzas: List<List<String>>,
    title: String?,
    meta: String?,
    flavor: KhetiFlavor,
    size: KhetiMetrics.Size,
    fonts: KhetiFontFamilies,
    colors: KhetiColors,
): List<KhetiVerticalBlockSpec> {
    val out = ArrayList<KhetiVerticalBlockSpec>(stanzas.size + 2)
    if (!title.isNullOrEmpty()) {
        val heading = KhetiTextStyle.heading(KhetiMetrics.Heading.H2, flavor, fonts)
        // 标题与左侧正文之间留半个列宽，避免因字号差导致贴太近
        val gap = KhetiMetrics.Heading.H2.lineHeightPx * 0.5f
        out += KhetiVerticalBlockSpec(title, heading, colors.ink, gapPx = gap)
    }
    if (!meta.isNullOrEmpty()) {
        val metaStyle = KhetiTextStyle.body(KhetiMetrics.Size.Small, flavor, fonts)
        out += KhetiVerticalBlockSpec(
            text = meta,
            style = metaStyle,
            color = colors.inkSecondary,
            gapPx = KhetiMetrics.Size.Small.lineHeightPx * 0.4f,
        )
    }
    for (stanza in stanzas) {
        if (stanza.isEmpty()) continue
        out += KhetiVerticalBlockSpec(
            text = stanza.joinToString("\n"),
            style = KhetiTextStyle.body(size, flavor, fonts),
            color = colors.ink,
        )
    }
    return out
}

private fun semanticPoemText(title: String?, meta: String?, stanzas: List<List<String>>): String =
    buildString {
        if (!title.isNullOrEmpty()) append(title).append('\n')
        if (!meta.isNullOrEmpty()) append(meta).append('\n')
        for (s in stanzas) for (line in s) append(line).append('\n')
    }.trimEnd()
