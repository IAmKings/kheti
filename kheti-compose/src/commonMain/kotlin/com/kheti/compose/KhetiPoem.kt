package com.kheti.compose

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.kheti.KhetiMetrics
import com.kheti.layout.KhetiAlignment
import com.kheti.layout.KhetiHang
import com.kheti.layout.KhetiTextStyle

/**
 * 诗词版式（上游 `.heti--poetry` + 官方示例写法）：
 *
 * - 正文**宋体**、**居中**、**无首行缩进**；
 * - 标题**楷体 + 800 字重 + 居中**（传统风格）；
 * - 元信息（朝代/作者）小一号、次级颜色、居中；
 * - 各诗节（`stanzas` 的每一项）是一个段落，行内用硬换行，段间距 24px；
 * - 行末标点**自动悬挂**（上游需手写 `<span class="heti-hang">`，此处自动化）。
 *
 * 纯规格构造放在 [khetiPoemSpecs]，便于无组合环境下测试与渲染证据。
 */
@Composable
fun KhetiPoem(
    stanzas: List<List<String>>,
    modifier: Modifier = Modifier,
    title: String? = null,
    meta: String? = null,
    flavor: KhetiFlavor = KhetiFlavor.Classic,
    size: KhetiMetrics.Size = KhetiMetrics.Size.XLarge,
    hang: KhetiHang = KhetiHang.LineEnd,
    maxLineLengthEm: Float = KhetiMetrics.LINE_LENGTH_EM,
) {
    val fonts = LocalKhetiFontFamilies.current
    val colors = LocalKhetiColors.current
    val specs = khetiPoemSpecs(
        stanzas = stanzas,
        title = title,
        meta = meta,
        flavor = flavor,
        size = size,
        hang = hang,
        fonts = fonts,
        colors = colors,
    )
    val semantic = buildString {
        if (title != null) append(title).append('\n')
        if (meta != null) append(meta).append('\n')
        stanzas.forEach { it.forEach { line -> append(line).append('\n') } }
    }.trimEnd()
    KhetiBlocksView(specs, maxLineLengthEm, modifier, semantic)
}

/**
 * 构造诗词块规格（纯函数，供测试与无组合渲染使用）。
 *
 * 边距取值遵循上游：标题用"亲密性"边距（下边距小）、段落用 0.5 网格顶 / 1 网格底。
 */
fun khetiPoemSpecs(
    stanzas: List<List<String>>,
    title: String?,
    meta: String?,
    flavor: KhetiFlavor,
    size: KhetiMetrics.Size,
    hang: KhetiHang,
    fonts: KhetiFontFamilies,
    colors: KhetiColors,
): List<KhetiBlockSpec> {
    val out = ArrayList<KhetiBlockSpec>(stanzas.size + 2)
    if (!title.isNullOrEmpty()) {
        out += KhetiBlockSpec(
            text = title,
            style = KhetiTextStyle.heading(KhetiMetrics.Heading.H2, flavor, fonts),
            alignment = KhetiAlignment.Center,
            marginTop = KhetiMetrics.GRID_UNIT,
            marginBottom = KhetiMetrics.PARAGRAPH_MARGIN_TOP,
            color = colors.ink,
        )
    }
    if (!meta.isNullOrEmpty()) {
        out += KhetiBlockSpec(
            text = meta,
            style = KhetiTextStyle.body(KhetiMetrics.Size.Small, flavor, fonts),
            alignment = KhetiAlignment.Center,
            marginTop = 0f,
            marginBottom = KhetiMetrics.PARAGRAPH_MARGIN_TOP,
            color = colors.inkSecondary,
        )
    }
    for (stanza in stanzas) {
        if (stanza.isEmpty()) continue
        out += KhetiBlockSpec(
            text = stanza.joinToString("\n"),
            style = KhetiTextStyle.body(size, flavor, fonts),
            alignment = KhetiAlignment.Center,
            hang = hang,
            marginTop = KhetiMetrics.PARAGRAPH_MARGIN_TOP,
            marginBottom = KhetiMetrics.PARAGRAPH_MARGIN_BOTTOM,
            color = colors.ink,
        )
    }
    return out
}
