package com.kheti.compose

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import com.kheti.KhetiMetrics
import com.kheti.layout.KhetiEngine
import com.kheti.layout.KhetiTextStyle
import com.kheti.layout.columnCountForWidth
import com.kheti.layout.drawKhetiMultiColumn
import com.kheti.layout.splitIntoColumns

/**
 * 分栏规格（纯计算，便于测试）。
 *
 * [count] 与 [columnWidthPx] 二者取一：
 * - 给出 [count] → 上游 `--columns-2/3/4`（CSS `column-count`）
 * - 给出 [columnWidthPx] → 上游 `--columns-Nem`（CSS `column-width`，按可用宽反推栏数）
 */
data class KhetiColumnsSpec(val count: Int, val columnWidthPx: Int, val gapPx: Float) {
    val totalWidthPx: Int get() = columnWidthPx * count + (gapPx * (count - 1)).toInt()
}

/** 由可用宽 + 栏数或栏宽，算出最终栏数与每栏宽度（栏间距从可用宽中扣除）。 */
fun khetiColumnsSpec(
    availableWidthPx: Int,
    count: Int? = null,
    columnWidthPx: Float? = null,
    gapPx: Float = 0f,
): KhetiColumnsSpec {
    val n = when {
        count != null && count > 0 -> count
        columnWidthPx != null && columnWidthPx > 0f -> columnCountForWidth(availableWidthPx, columnWidthPx, gapPx)
        else -> 1
    }
    val totalGap = gapPx * (n - 1)
    val w = ((availableWidthPx - totalGap) / n).toInt().coerceAtLeast(1)
    return KhetiColumnsSpec(n, w, gapPx)
}

/**
 * 多栏文本（上游 `--columns-2/3/4` 与 `--columns-Nem`）。
 *
 * - 传 [count] → 按栏数（`column-count`）
 * - 传 [columnWidthEm] 且 [count] = null → 按栏宽（`column-width`），栏数由可用宽反推
 *
 * 实现复用现有引擎：先排成单栏，再按 `column-fill: balance` 切分（见 `splitIntoColumns`），
 * 因此 ruby / 行内样式 / 悬挂等能力在各栏内自动生效。
 */
@Composable
fun KhetiColumns(
    text: String,
    modifier: Modifier = Modifier,
    /** 栏数（上游 `--columns-N`）；与 [columnWidthEm] 二选一。 */
    count: Int? = 2,
    /** 栏宽（em，上游 `--columns-Nem`）；给出且 [count] = null 时按栏宽分栏。 */
    columnWidthEm: Float? = null,
    /** 栏间距（em）。上游未显式设 `column-gap`，此处默认 1em。 */
    gapEm: Float = 1f,
    flavor: KhetiFlavor = KhetiFlavor.Serif,
    size: KhetiMetrics.Size = KhetiMetrics.Size.Normal,
    spacing: Boolean = true,
    color: Color = Color.Unspecified,
) {
    val density = LocalDensity.current
    val fonts = LocalKhetiFontFamilies.current
    val colors = LocalKhetiColors.current
    val style = remember(size, flavor, fonts) { KhetiTextStyle.body(size, flavor, fonts) }
    val resolvedColor = if (color == Color.Unspecified) colors.ink else color
    val measurer = rememberTextMeasurer()
    val engine = remember(measurer, density) { KhetiEngine(measurer, density) }
    val fontPx = with(density) { style.fontSize.toPx() }
    val gapPx = gapEm * fontPx

    BoxWithConstraints(modifier) {
        val available = if (constraints.hasBoundedWidth) constraints.maxWidth else 2000
        val spec = remember(available, count, columnWidthEm, gapEm, fontPx) {
            khetiColumnsSpec(
                availableWidthPx = available,
                count = count,
                columnWidthPx = columnWidthEm?.let { it * fontPx },
                gapPx = gapPx,
            )
        }
        val multi = remember(text, style, spec, spacing) {
            val base = engine.layout(text, style, maxWidthPx = spec.columnWidthPx, spacing = spacing)
            splitIntoColumns(base, count = spec.count.coerceAtLeast(1), gapPx = spec.gapPx)
        }

        Canvas(
            Modifier
                .layout { measurable, _ ->
                    val w = multi.size.width.coerceAtLeast(1)
                    val h = multi.size.height.coerceAtLeast(1)
                    val placeable = measurable.measure(
                        Constraints(minWidth = w, maxWidth = w, minHeight = h, maxHeight = h)
                    )
                    layout(w, h) { placeable.placeRelative(0, 0) }
                }
                .semantics { this.text = AnnotatedString(text) },
        ) {
            drawKhetiMultiColumn(
                measurer = measurer,
                multi = multi,
                style = style,
                color = resolvedColor,
                density = density,
            )
        }
    }
}
