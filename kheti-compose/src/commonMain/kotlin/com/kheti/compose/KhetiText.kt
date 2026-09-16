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
import com.kheti.KhetiMetrics
import com.kheti.layout.KhetiAlignment
import com.kheti.layout.KhetiEngine
import com.kheti.layout.KhetiHang
import com.kheti.layout.KhetiInlineSpan
import com.kheti.layout.KhetiLang
import com.kheti.layout.KhetiRuby
import com.kheti.layout.KhetiRubyMode
import com.kheti.layout.KhetiTextStyle
import com.kheti.layout.drawKhetiText

/**
 * kheti 正文文本：横排、自动施加赫蹏规则（中西文 ¼ 字宽、标点 ½/¼ 字宽挤压、0.02em 字距）。
 *
 * - 以 `\n` 分段；段间距按赫蹏规则（顶 0.5 网格 / 底 1 网格，折叠后 = 24px）。
 * - 行宽默认上限 42em（上游 `.heti { max-width: 42em }`）。
 * - **展示型**：不支持 IME 编辑（与 Android 官方竖排同定位）；无障碍通过 `text` 语义暴露。
 */
@Composable
fun KhetiText(
    text: String,
    modifier: Modifier = Modifier,
    size: KhetiMetrics.Size = KhetiMetrics.Size.Normal,
    flavor: KhetiFlavor = KhetiFlavor.Serif,
    alignment: KhetiAlignment = KhetiAlignment.Start,
    hang: KhetiHang = KhetiHang.Off,
    /** 首行缩进（em）。古文版式传 2f。 */
    firstLineIndentEm: Float = 0f,
    maxLineLengthEm: Float = KhetiMetrics.LINE_LENGTH_EM,
    spacing: Boolean = true,
    /** 着重号区间（相对 [text] 的下标）。 */
    emphasis: List<IntRange> = emptyList(),
    /** 行间注（下标基于 [text] 原文，可含被赫蹏吞掉的空格）。 */
    ruby: List<KhetiRuby> = emptyList(),
    rubyScale: Float = KhetiMetrics.RUBY_SCALE,
    /**
     * 行间注版式（上游 `.heti--annotation`）：行高 2.25em、段间距 0、首行缩进 2em，
     * 注音改为占据行盒上方（[KhetiRubyMode.Block]）。
     */
    annotationMode: Boolean = false,
    /** 行内元素样式区间（下标基于 [text] 原文）。 */
    inline: List<KhetiInlineSpan> = emptyList(),
    /** 容器语言：西文容器字距归零（上游 `[lang=en-US]`）。 */
    lang: KhetiLang = KhetiLang.Zh,
    color: Color = Color.Unspecified,
) {
    val fonts = LocalKhetiFontFamilies.current
    val baseStyle = remember(size, flavor, fonts) { KhetiTextStyle.body(size, flavor, fonts) }
    val style = remember(baseStyle, annotationMode) {
        if (annotationMode) {
            baseStyle.copy(lineHeight = baseStyle.fontSize * KhetiMetrics.LINE_HEIGHT_ANNOTATION)
        } else {
            baseStyle
        }
    }
    val rubyMode = if (annotationMode) KhetiRubyMode.Block else KhetiRubyMode.Inline
    // 必须在这里解析颜色：绘制层遇到 Color.Unspecified 会回退到**平台默认色（黑）**，
    // 深色模式下就成了黑字压深底 → 整片空白。KhetiPoem 一直显式传 colors.ink，
    // 而 KhetiText 之前漏了，因此只有它的内容在深色模式下不可见。
    val colors = LocalKhetiColors.current
    val resolvedColor = if (color == Color.Unspecified) colors.ink else color
    val indent = if (annotationMode && firstLineIndentEm == 0f) {
        KhetiMetrics.TEXT_INDENT_EM
    } else {
        firstLineIndentEm
    }
    // 上游 `--annotation` 下段落 margin-block 归零：间距完全由 2.25 倍行高提供
    val marginTop = if (annotationMode) 0f else KhetiMetrics.PARAGRAPH_MARGIN_TOP
    val marginBottom = if (annotationMode) 0f else KhetiMetrics.PARAGRAPH_MARGIN_BOTTOM

    val specs = remember(
        text, style, alignment, hang, spacing, indent, emphasis, resolvedColor,
        ruby, rubyMode, rubyScale, marginTop, marginBottom, inline, lang,
    ) {
        buildParagraphSpecs(
            text = text,
            style = style,
            alignment = alignment,
            hang = hang,
            spacing = spacing,
            firstLineIndentEm = indent,
            emphasis = emphasis,
            ruby = ruby,
            rubyMode = rubyMode,
            rubyScale = rubyScale,
            inline = inline,
            lang = lang,
            marginTop = marginTop,
            marginBottom = marginBottom,
            color = resolvedColor,
        )
    }

    KhetiBlocksView(specs, maxLineLengthEm, modifier, text)
}

/** 把 `\n` 分段文本切成块规格，并把着重号/行间注下标平移到各段内。 */
internal fun buildParagraphSpecs(
    text: String,
    style: KhetiTextStyle,
    alignment: KhetiAlignment,
    hang: KhetiHang,
    spacing: Boolean,
    firstLineIndentEm: Float,
    emphasis: List<IntRange>,
    ruby: List<KhetiRuby> = emptyList(),
    rubyMode: KhetiRubyMode = KhetiRubyMode.Inline,
    rubyScale: Float = KhetiMetrics.RUBY_SCALE,
    /** 行内元素样式区间（下标基于 [text] 原文）。 */
    inline: List<KhetiInlineSpan> = emptyList(),
    /** 容器语言：西文容器字距归零（上游 `[lang=en-US]`）。 */
    lang: KhetiLang = KhetiLang.Zh,
    marginTop: Float = KhetiMetrics.PARAGRAPH_MARGIN_TOP,
    marginBottom: Float = KhetiMetrics.PARAGRAPH_MARGIN_BOTTOM,
    color: Color,
): List<KhetiBlockSpec> {
    val paragraphs = text.split('\n')
    val out = ArrayList<KhetiBlockSpec>(paragraphs.size)
    var offset = 0
    for (p in paragraphs) {
        if (p.isNotEmpty()) {
            val local = emphasis.mapNotNull { r ->
                val s = (r.first - offset).coerceAtLeast(0)
                val e = (r.last - offset).coerceAtMost(p.length - 1)
                if (e >= s) s..e else null
            }
            val localRuby = ruby.mapNotNull { r ->
                val s = (r.start - offset).coerceAtLeast(0)
                val e = (r.end - offset).coerceAtMost(p.length)
                if (e > s) KhetiRuby(s, e, r.annotation) else null
            }
            val localInline = inline.mapNotNull { sp ->
                val s = (sp.start - offset).coerceAtLeast(0)
                val e = (sp.end - offset).coerceAtMost(p.length)
                if (e > s) KhetiInlineSpan(s, e, sp.kind, sp.color) else null
            }
            out += KhetiBlockSpec(
                text = p,
                style = style,
                alignment = alignment,
                hang = hang,
                spacing = spacing,
                firstLineIndentEm = firstLineIndentEm,
                marginTop = marginTop,
                marginBottom = marginBottom,
                color = color,
                emphasis = local,
                ruby = localRuby,
                rubyMode = rubyMode,
                rubyScale = rubyScale,
                inline = localInline,
                lang = lang,
            )
        }
        offset += p.length + 1 // + '\n'
    }
    return out
}

/**
 * 块序列的绘制入口。
 *
 * 宽度来自父约束（并与 `maxLineLengthEm × 字号` 取较小值），高度由排版结果决定。
 */
@Composable
internal fun KhetiBlocksView(
    specs: List<KhetiBlockSpec>,
    maxLineLengthEm: Float,
    modifier: Modifier,
    semanticText: String,
) {
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val engine = remember(measurer, density) { KhetiEngine(measurer, density) }
    val showGrid = LocalKhetiShowGrid.current

    BoxWithConstraints(modifier) {
        val constraintPx = constraints.maxWidth
        val refFontPx = with(density) { (specs.firstOrNull()?.style ?: KhetiTextStyle()).fontSize.toPx() }
        val capPx = (maxLineLengthEm * refFontPx).toInt()
        val limit = minOf(constraintPx, capPx).coerceAtLeast(1)

        val blockLayout = remember(specs, limit) { layoutKhetiBlocks(engine, specs, limit) }

        Canvas(
            Modifier
                .layout { measurable, _ ->
                    val w = blockLayout.width
                    val h = blockLayout.height
                    val placeable = measurable.measure(
                        androidx.compose.ui.unit.Constraints(
                            minWidth = w, maxWidth = w,
                            minHeight = h, maxHeight = h,
                        )
                    )
                    layout(w, h) { placeable.placeRelative(0, 0) }
                }
                .semantics { this.text = AnnotatedString(semanticText) },
        ) {
            if (showGrid) {
                // 横排网格：按布局自身的行盒顶边画线（因此与 x-large/annotation 等
                // 任意行高都精确对齐，而不是固定 24dp）
                val rule = Color.Gray.copy(alpha = 0.35f)
                val w = blockLayout.width.toFloat()
                for (b in blockLayout.blocks) {
                    for (line in b.layout.lines) {
                        val y = b.top + line.top
                        drawLine(rule, Offset(0f, y), Offset(w, y), strokeWidth = 1f)
                    }
                }
            }
            for (b in blockLayout.blocks) {
                drawKhetiText(
                    measurer = measurer,
                    layout = b.layout,
                    style = b.style,
                    color = b.color,
                    density = density,
                    topOffset = b.top,
                    emphasis = b.emphasis,
                )
            }
        }
    }
}
