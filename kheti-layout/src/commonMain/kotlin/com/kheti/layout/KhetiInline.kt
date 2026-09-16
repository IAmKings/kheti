package com.kheti.layout

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope

/**
 * 行内元素类型（对应上游 `lib/_inline.scss` 里的标签样式）。
 *
 * 上游是给 HTML 标签配 CSS；Compose 没有标签，因此改为**区间 + 类型**表达。
 */
enum class KhetiInlineKind {
    /** `a`：链接色 + 下划线。 */
    Link,

    /** `abbr`：点状下划线。 */
    Abbr,

    /** `u`：专名号（实线下划线）。 */
    Proper,

    /** `del`：删除线。 */
    Delete,

    /** `ins`：下划线。 */
    Insert,

    /** `s`：删除线。 */
    Strike,

    /** `dfn`：术语（斜体）。 */
    Term,

    /** `mark`：底色高亮。 */
    Mark,

    /** `code`：等宽 + 底色。 */
    Code,

    /** `em`：强调（斜体）。着重号另见 `emphasis` 参数。 */
    Emphasis,

    /** `sup`：上标（缩小 + 上移）。脚注角标用它。 */
    Sup,

    /** `sub`：下标（缩小 + 下移）。 */
    Sub,

    /** `small`：小一号（上游 `$font-size-small` 14px / 基准 16px）。 */
    Small,
    ;

    val underline: Boolean
        get() = this == Link || this == Proper || this == Insert

    val strikethrough: Boolean
        get() = this == Delete || this == Strike

    val dottedUnderline: Boolean
        get() = this == Abbr

    val background: Boolean
        get() = this == Mark || this == Code

    val italic: Boolean
        get() = this == Term || this == Emphasis

    /**
     * 字号缩放。**注意：会改变 advance**，因此引擎必须按缩放后的样式单独度量该字，
     * 并在缩放边界切段（否则绘制时字形大小不会变）。
     */
    val scale: Float
        get() = when (this) {
            Sup, Sub -> 0.8f
            Small -> 0.875f
            else -> 1f
        }

    /** 基线位移（em，负=上移）。 */
    val shiftEm: Float
        get() = when (this) {
            Sup -> -0.35f
            Sub -> 0.2f
            else -> 0f
        }
}

/**
 * 行内区间样式；下标基于**原文**（引擎会按归一化下标重新定位，
 * 与 ruby 使用同一套映射）。
 */
data class KhetiInlineSpan(
    val start: Int,
    val end: Int,
    val kind: KhetiInlineKind,
    /** 可选覆盖色（如链接色）；[Color.Unspecified] 表示用主题色。 */
    val color: Color = Color.Unspecified,
)

/**
 * 把区间解析成**逐字符**效果表，供度量与绘制直接索引。
 *
 * 使用逐字符数组而不是区间查询，是为了让引擎与绘制层保持 O(1) 取用、
 * 且区间跨行时无需特殊处理。
 */
class KhetiInlineEffects(
    chars: Int,
    spans: List<KhetiInlineSpan>,
    plan: com.kheti.PlannedText,
) {
    val underline = BooleanArray(chars)
    val strikethrough = BooleanArray(chars)
    val dotted = BooleanArray(chars)
    val background = BooleanArray(chars)
    val italic = BooleanArray(chars)
    val color = arrayOfNulls<Color>(chars)

    /** 逐字符缩放（默认 1）：上下标与小号会改变 advance。 */
    val scale = FloatArray(chars) { 1f }

    /** 逐字符基线位移（em）。 */
    val shiftEm = FloatArray(chars)

    init {
        for (span in spans) {
            if (span.end <= span.start || chars == 0) continue
            val s = plan.plannedIndex(span.start.coerceIn(0, maxOf(0, span.start)))
            val e = plan.plannedIndex((span.end - 1).coerceAtLeast(span.start)) + 1
            for (i in s.coerceAtLeast(0) until e.coerceAtMost(chars)) {
                if (span.kind.underline) underline[i] = true
                if (span.kind.strikethrough) strikethrough[i] = true
                if (span.kind.dottedUnderline) dotted[i] = true
                if (span.kind.background) background[i] = true
                if (span.kind.italic) italic[i] = true
                if (span.kind.scale != 1f) scale[i] = span.kind.scale
                if (span.kind.shiftEm != 0f) shiftEm[i] = span.kind.shiftEm
                if (span.color != Color.Unspecified) color[i] = span.color
            }
        }
    }

    val isEmpty: Boolean
        get() = !underline.any { it } && !strikethrough.any { it } && !dotted.any { it } &&
            !background.any { it } && !italic.any { it } && color.none { it != null } &&
            scale.all { it == 1f } && shiftEm.all { it == 0f }
}

/** 默认装饰色：与文字同色但更淡，贴近上游 `text-decoration-color` 的观感。 */
private fun decorationColor(textColor: Color) =
    if (textColor == Color.Unspecified) Color(0xFF666666) else textColor

/**
 * 画行内装饰：底色块、下划线/点线、删除线。
 *
 * 只负责"装饰"，文字本身仍由 [drawKhetiText] 绘制 —— 因此调用顺序为：
 * 先本函数（底色在文字之下），再画文字，最后可选再画线（线在文字之上）。
 */
fun DrawScope.drawKhetiInlineDecorations(
    layout: KhetiLayout,
    effects: KhetiInlineEffects,
    textColor: Color = Color.Unspecified,
    leftOffset: Float = 0f,
    topOffset: Float = 0f,
    /** true = 只画底色（应在文字之前调用）；false = 只画线（应在文字之后调用）。 */
    backgroundPass: Boolean = true,
) {
    if (effects.isEmpty) return
    val rule = decorationColor(textColor)
    val bg = Color(0x1F000000)

    for (line in layout.lines) {
        for (k in 0 until line.length) {
            val i = line.start + k
            val x0 = leftOffset + line.x + line.charX[k]
            val w = line.charAdvance[k]
            val yTop = topOffset + line.top
            val yBottom = yTop + layout.lineHeightPx
            if (backgroundPass) {
                if (effects.background[i]) {
                    drawRect(
                        color = bg,
                        topLeft = Offset(x0, yTop + layout.lineHeightPx * 0.08f),
                        size = androidx.compose.ui.geometry.Size(w, layout.lineHeightPx * 0.84f),
                    )
                }
            } else {
                // 下划线略低于基文；删除线约在中部
                if (effects.underline[i]) {
                    val y = yTop + layout.lineHeightPx * 0.78f
                    drawLine(rule, Offset(x0, y), Offset(x0 + w, y), strokeWidth = 1.2f)
                }
                if (effects.dotted[i]) {
                    val y = yTop + layout.lineHeightPx * 0.78f
                    var x = x0
                    while (x < x0 + w) {
                        drawLine(rule, Offset(x, y), Offset(minOf(x + 1.5f, x0 + w), y), strokeWidth = 1.2f)
                        x += 3.5f
                    }
                }
                if (effects.strikethrough[i]) {
                    val y = yTop + layout.lineHeightPx * 0.52f
                    drawLine(rule, Offset(x0, y), Offset(x0 + w, y), strokeWidth = 1.2f)
                }
            }
        }
    }
}
