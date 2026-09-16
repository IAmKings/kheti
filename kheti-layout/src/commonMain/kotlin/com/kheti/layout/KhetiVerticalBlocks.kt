package com.kheti.layout

import androidx.compose.ui.graphics.Color
import com.kheti.KhetiRegion

/**
 * 竖排的块级规格：标题 / 元信息 / 诗节各成一块，**各自持有样式**，
 * 因此竖排也能保持横排的字号、字重、字体族与颜色层级。
 */
data class KhetiVerticalBlockSpec(
    val text: String,
    val style: KhetiTextStyle,
    val color: Color = Color.Unspecified,
    /** 该块与其**左侧**相邻块之间的额外间距（px）。 */
    val gapPx: Float = 0f,
    val alignment: KhetiVerticalAlignment = KhetiVerticalAlignment.Top,
)

/** 已排好版的竖排块。 */
class KhetiVerticalBlock(
    val layout: KhetiVerticalLayout,
    val style: KhetiTextStyle,
    /** 块左边界（整幅坐标）。 */
    val x: Float,
    val color: Color,
)

class KhetiVerticalBlockLayout(
    val blocks: List<KhetiVerticalBlock>,
    val width: Int,
    val height: Int,
)

/**
 * 竖排块排版：块按**右 → 左**排列（首块在最右），与竖排阅读顺序一致。
 *
 * 每个块内部仍可含 `\n`，各自再分列（例如一个诗节的多句）。
 */
fun layoutKhetiVerticalBlocks(
    engine: KhetiVerticalEngine,
    specs: List<KhetiVerticalBlockSpec>,
    maxHeightPx: Int,
    region: KhetiRegion = KhetiRegion.CN,
    hang: KhetiHang = KhetiHang.LineEnd,
    spacing: Boolean = true,
): KhetiVerticalBlockLayout {
    val laid = specs.filter { it.text.isNotEmpty() }.map {
        it to engine.layout(
            text = it.text,
            style = it.style,
            maxHeightPx = maxHeightPx,
            alignment = it.alignment,
            hang = hang,
            spacing = spacing,
            region = region,
        )
    }
    if (laid.isEmpty()) return KhetiVerticalBlockLayout(emptyList(), 0, 0)

    var total = 0f
    for ((spec, l) in laid) total += l.size.width + spec.gapPx

    var cursor = 0f
    val blocks = ArrayList<KhetiVerticalBlock>(laid.size)
    for ((spec, l) in laid) {
        blocks += KhetiVerticalBlock(
            layout = l,
            style = spec.style,
            x = total - cursor - l.size.width,
            color = spec.color,
        )
        cursor += l.size.width + spec.gapPx
    }
    return KhetiVerticalBlockLayout(
        blocks = blocks,
        width = total.toInt(),
        height = laid.maxOf { it.second.size.height },
    )
}
