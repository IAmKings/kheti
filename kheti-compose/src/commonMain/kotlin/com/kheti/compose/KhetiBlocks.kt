package com.kheti.compose

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import com.kheti.layout.KhetiAlignment
import com.kheti.layout.KhetiEngine
import com.kheti.layout.KhetiHang
import com.kheti.layout.KhetiInlineSpan
import com.kheti.layout.KhetiLang
import com.kheti.layout.KhetiLayout
import com.kheti.layout.KhetiRuby
import com.kheti.layout.KhetiRubyMode
import com.kheti.layout.KhetiTextStyle

/**
 * 一个块级元素（段落/标题/诗节）的规格。
 *
 * 边距遵循赫蹏的设计原则："块级元素采用一行行高作为底边距，半行行高作为顶边距"，
 * 相邻块之间的外边距按 CSS 规则**折叠**（取最大值）——因此段间距 = 24px = 1 个垂直网格。
 */
@Immutable
data class KhetiBlockSpec(
    val text: String,
    val style: KhetiTextStyle,
    val alignment: KhetiAlignment = KhetiAlignment.Start,
    val hang: KhetiHang = KhetiHang.Off,
    val spacing: Boolean = true,
    /** 首行缩进（em）：上游 `.heti--ancient p { text-indent: 2em }`。 */
    val firstLineIndentEm: Float = 0f,
    val marginTop: Float = 0f,
    val marginBottom: Float = 0f,
    val color: Color = Color.Unspecified,
    val emphasis: List<IntRange> = emptyList(),
    /** 行间注（下标基于本块文本）。 */
    val ruby: List<KhetiRuby> = emptyList(),
    val rubyMode: KhetiRubyMode = KhetiRubyMode.Inline,
    val rubyScale: Float = 0.5f,
    /** 行内元素样式（`a`/`abbr`/`u`/`del`/`mark`/`code`…；下标基于本块文本）。 */
    val inline: List<KhetiInlineSpan> = emptyList(),
    /** 容器语言：西文容器字距归零（上游 `[lang=en-US]`）。 */
    val lang: KhetiLang = KhetiLang.Zh,
)

/** 已排好版的块：几何 + 绘制所需的一切。 */
@Immutable
class KhetiBlock(
    val layout: KhetiLayout,
    val style: KhetiTextStyle,
    val top: Float,
    val color: Color,
    val emphasis: List<IntRange>,
)

/** 块序列的整体排版结果。 */
@Immutable
class KhetiBlockLayout(
    val blocks: List<KhetiBlock>,
    val width: Int,
    val height: Int,
)

/**
 * 纯函数版块排版：**不依赖 Compose 组合**，因此可在测试里直接渲染成 PNG 作为验收证据。
 *
 * 外边距折叠用"取最大值"实现（与浏览器一致）：赫蹏的 12px 顶边距 / 24px 底边距
 * 折叠后段间距恰为 24px = 1 个垂直网格单位。
 */
fun layoutKhetiBlocks(
    engine: KhetiEngine,
    specs: List<KhetiBlockSpec>,
    widthPx: Int,
): KhetiBlockLayout {
    val blocks = ArrayList<KhetiBlock>(specs.size)
    var y = 0f
    var pendingGap = 0f
    var first = true
    for (spec in specs) {
        if (spec.text.isEmpty()) continue
        // 上游 `.heti > *:first-child { margin-block-start: 0 !important }`：首元素顶边距清零
        pendingGap = if (first) 0f else maxOf(pendingGap, spec.marginTop)
        first = false
        y += pendingGap
        val layout = engine.layout(
            text = spec.text,
            style = spec.style,
            maxWidthPx = widthPx,
            alignment = spec.alignment,
            hang = spec.hang,
            spacing = spec.spacing,
            firstLineIndentEm = spec.firstLineIndentEm,
            ruby = spec.ruby,
            rubyMode = spec.rubyMode,
            rubyScale = spec.rubyScale,
            inline = spec.inline,
            lang = spec.lang,
        )
        blocks += KhetiBlock(layout, spec.style, y, spec.color, spec.emphasis)
        y += layout.size.height
        pendingGap = spec.marginBottom
    }
    return KhetiBlockLayout(blocks, widthPx, y.toInt())
}
