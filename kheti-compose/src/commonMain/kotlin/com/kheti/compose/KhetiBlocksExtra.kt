package com.kheti.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kheti.KhetiMetrics
import com.kheti.layout.KhetiAlignment
import com.kheti.layout.KhetiTextStyle

/**
 * D12 块级元素（对应上游 `lib/_base.scss`、`_heading.scss`、`_list.scss`、`_table.scss`）。
 *
 * 这些是**块级几何**：边距/内边距/背景/对齐。数值逐条取自上游 SCSS，
 * 目的是让长文（散文/文章）排版与赫蹏一致 —— 诗词主线用不到它们，但演示页有。
 */
object KhetiBlocks {

    /** `blockquote`：块向外边距 0.5 / 1 网格（上/下），两侧 2em。 */
    val BLOCKQUOTE_MARGIN_TOP = KhetiMetrics.GRID_UNIT * 0.5f
    val BLOCKQUOTE_MARGIN_BOTTOM = KhetiMetrics.GRID_UNIT
    val BLOCKQUOTE_MARGIN_INLINE_EM = 2f

    /** `blockquote` 内边距：上下 0.5 网格，左右 1em。 */
    val BLOCKQUOTE_PADDING_BLOCK = KhetiMetrics.GRID_UNIT * 0.5f
    val BLOCKQUOTE_PADDING_INLINE_EM = 1f

    /** `blockquote` / `pre` 背景：`hsla(0, 0%, 0%, .054)`。 */
    const val SURFACE_ALPHA = 0.054f

    /** `hr`：宽 30%、高 1px、上下各 2 网格、水平居中、颜色 `hsl(0,0%,80%)`。 */
    const val HR_WIDTH_FRACTION = 0.30f
    const val HR_HEIGHT = 1f
    val HR_MARGIN_BLOCK = KhetiMetrics.GRID_UNIT * 2f

    /** 深色模式下的分隔线 / 背景色（上游用 `prefers-color-scheme` 切换）。 */
    val HR_COLOR_LIGHT = Color(0xFFCCCCCC)
    val HR_COLOR_DARK = Color(0xFF404040)

    // ---- 标题（上游 `_heading.scss`；下列值取自编译产物 `heti.min.css`）----

    /** 基础标题字重 **600**（`$font-weight-bold`）；诗词/古文 modifier 才覆盖为 800。 */
    const val HEADING_WEIGHT = 600

    /** 标题顶边距（所有级别相同）= 1 网格。 */
    val HEADING_MARGIN_TOP = KhetiMetrics.GRID_UNIT

    /** 标题底边距（h2–h6）= 0.5 网格。 */
    val HEADING_MARGIN_BOTTOM = KhetiMetrics.PARAGRAPH_MARGIN_TOP

    /** h1 底边距 = 1 网格（上游单独覆盖为 24px）。 */
    val HEADING_H1_MARGIN_BOTTOM = KhetiMetrics.GRID_UNIT

    /** h1–h3 额外字距（上游 `.heti h1,h2,h3 { letter-spacing: .05em }`）。 */
    const val HEADING_LETTER_SPACING_LARGE_EM = 0.05f

    // ---- 表格（上游 `_table.scss`）----

    val TABLE_MARGIN_TOP = KhetiMetrics.PARAGRAPH_MARGIN_TOP
    val TABLE_MARGIN_BOTTOM = KhetiMetrics.GRID_UNIT
    const val TABLE_CELL_PADDING_BLOCK = 6f
    const val TABLE_CELL_PADDING_INLINE = 8f
    val TABLE_BORDER_LIGHT = Color(0xFFCCCCCC)
    val TABLE_BORDER_DARK = Color(0xFF404040)

    /** 表注在**下方**（上游 `caption { caption-side: bottom }`）。 */
    const val TABLE_CAPTION_BELOW = true
    const val CAPTION_FONT_SIZE = 14f
    const val CAPTION_LINE_HEIGHT = 24f

    // ---- 脚注（上游 `lib/helpers/_block.scss` 的 `.heti-fn`）----

    /** 页脚与正文之间留出的距离（上游 `margin-block-start: 59px`）。 */
    const val FOOTNOTE_MARGIN_TOP = 59f

    /** 页脚顶部 1px 分隔线颜色：`hsl(0,0%,80%)` / 深色 `hsl(0,0%,25%)`。 */
    val FOOTNOTE_BORDER_LIGHT = Color(0xFFCCCCCC)
    val FOOTNOTE_BORDER_DARK = Color(0xFF404040)

    /** 页脚字号/行高：`$font-size-small` = 14px、`$line-height-size-normal` = 24px。 */
    const val FOOTNOTE_FONT_SIZE = 14f
    const val FOOTNOTE_LINE_HEIGHT = 24f

    /** 页脚内 `ol` 的顶边距 = 0.5 网格。 */
    val FOOTNOTE_LIST_MARGIN_TOP = KhetiMetrics.GRID_UNIT * 0.5f

    /** `li:target` 高亮：`hsl(210,100%,93%)` / 深色 `hsl(210,40%,38%)`。 */
    val FOOTNOTE_HIGHLIGHT_LIGHT = Color(0xFFDBEDFF)
    val FOOTNOTE_HIGHLIGHT_DARK = Color(0xFF3A6188)
}

/**
 * 列表标记（对应上游 `_list.scss` 与 `heti-list-latin` / `heti-list-han`）。
 *
 * 上游用 CSS `list-style-type`，Compose 没有，因此由我们生成标记文本。
 */
enum class KhetiListMarker {
    /** 无序：`•`。 */
    Bullet,

    /** `decimal`：1. 2. */
    Decimal,

    /** `cjk-ideographic`：一、二、…（上游 `heti-list-han`）。 */
    HanIdeographic,

    /** `upper-latin`：A. B.（上游 `heti-list-latin` 顶层）。 */
    UpperLatin,

    /** `lower-roman`：i. ii.（上游 `heti-list-latin` 第二层）。 */
    LowerRoman,
}

/** 生成第 [index]（从 1 开始）项的列表标记文本。 */
fun khetiListMarker(index: Int, marker: KhetiListMarker): String = when (marker) {
    KhetiListMarker.Bullet -> "• "
    KhetiListMarker.Decimal -> "$index. "
    KhetiListMarker.HanIdeographic -> hanNumeral(index) + "、"
    KhetiListMarker.UpperLatin -> "${('A' + ((index - 1) % 26))}. "
    KhetiListMarker.LowerRoman -> lowerRoman(index) + ". "
}

/** 中文数字（`cjk-ideographic` 的常用范围：1–99 足够列表用）。 */
internal fun hanNumeral(n: Int): String {
    if (n <= 0) return "零"
    val digits = "零一二三四五六七八九"
    if (n < 10) return digits[n].toString()
    val tens = n / 10
    val ones = n % 10
    val t = if (tens == 1) "十" else digits[tens] + "十"
    return if (ones == 0) t else t + digits[ones]
}

/** 小写罗马数字（1–3999）。 */
internal fun lowerRoman(n: Int): String {
    if (n !in 1..3999) return n.toString()
    val table = listOf(
        1000 to "m", 900 to "cm", 500 to "d", 400 to "cd",
        100 to "c", 90 to "xc", 50 to "l", 40 to "xl",
        10 to "x", 9 to "ix", 5 to "v", 4 to "iv", 1 to "i",
    )
    var v = n
    val sb = StringBuilder()
    for ((value, sym) in table) {
        while (v >= value) {
            sb.append(sym)
            v -= value
        }
    }
    return sb.toString()
}

// ---------------------------------------------------------------- 组合件

/**
 * 标题 `h1`–`h6`。
 *
 * 上游标题采用"亲密性原则"的**反向边距**（上大下小），并居中于传统风格。
 */
@Composable
fun KhetiHeading(
    text: String,
    level: KhetiMetrics.Heading = KhetiMetrics.Heading.H2,
    modifier: Modifier = Modifier,
    flavor: KhetiFlavor = KhetiFlavor.Classic,
    align: KhetiAlignment = KhetiAlignment.Start,
    maxLineLengthEm: Float = KhetiMetrics.LINE_LENGTH_EM,
) {
    val fonts = LocalKhetiFontFamilies.current
    val colors = LocalKhetiColors.current
    val base = KhetiTextStyle.heading(level, flavor, fonts)
    // 文章模式的标题是 600 字重（800 只属于诗词/古文 modifier）；
    // h1–h3 另加 0.05em 字距，h4–h6 用容器字距。
    val largeTracking = level == KhetiMetrics.Heading.H1 ||
        level == KhetiMetrics.Heading.H2 ||
        level == KhetiMetrics.Heading.H3
    val style = base.copy(
        fontWeight = androidx.compose.ui.text.font.FontWeight(KhetiBlocks.HEADING_WEIGHT),
        letterSpacingEm = if (largeTracking) {
            KhetiBlocks.HEADING_LETTER_SPACING_LARGE_EM
        } else {
            KhetiMetrics.LETTER_SPACING_CJK_EM
        },
    )
    KhetiBlocksView(
        specs = listOf(
            KhetiBlockSpec(
                text = text,
                style = style,
                alignment = align,
                marginTop = KhetiBlocks.HEADING_MARGIN_TOP,
                marginBottom = if (level == KhetiMetrics.Heading.H1) {
                    KhetiBlocks.HEADING_H1_MARGIN_BOTTOM
                } else {
                    KhetiBlocks.HEADING_MARGIN_BOTTOM
                },
                color = colors.ink,
            ),
        ),
        maxLineLengthEm = maxLineLengthEm,
        modifier = modifier,
        semanticText = text,
    )
}

/** 引用块 `blockquote`：缩进 + 内边距 + 浅色背景。 */
@Composable
fun KhetiBlockquote(
    text: String,
    modifier: Modifier = Modifier,
    flavor: KhetiFlavor = KhetiFlavor.Classic,
) {
    val fonts = LocalKhetiFontFamilies.current
    val colors = LocalKhetiColors.current
    val style = KhetiTextStyle.body(KhetiMetrics.Size.Normal, flavor, fonts)
    Box(
        modifier
            .fillMaxWidth()
            .padding(
                start = (KhetiBlocks.BLOCKQUOTE_MARGIN_INLINE_EM * KhetiMetrics.FONT_SIZE_NORMAL).dp,
                end = (KhetiBlocks.BLOCKQUOTE_MARGIN_INLINE_EM * KhetiMetrics.FONT_SIZE_NORMAL).dp,
            )
            .background(colors.ink.copy(alpha = KhetiBlocks.SURFACE_ALPHA))
            .padding(
                start = (KhetiBlocks.BLOCKQUOTE_PADDING_INLINE_EM * KhetiMetrics.FONT_SIZE_NORMAL).dp,
                end = (KhetiBlocks.BLOCKQUOTE_PADDING_INLINE_EM * KhetiMetrics.FONT_SIZE_NORMAL).dp,
            ),
    ) {
        KhetiBlocksView(
            specs = listOf(
                KhetiBlockSpec(
                    text = text,
                    style = style,
                    alignment = KhetiAlignment.Justify,
                    marginTop = 0f,
                    marginBottom = 0f,
                    color = colors.ink,
                ),
            ),
            maxLineLengthEm = KhetiMetrics.LINE_LENGTH_EM,
            modifier = Modifier,
            semanticText = text,
        )
    }
}

/**
 * 分隔线 `hr`：宽 30%、高 1px、水平居中、上下各 2 网格。
 */
@Composable
fun KhetiHr(modifier: Modifier = Modifier, dark: Boolean = false) {
    val color = if (dark) KhetiBlocks.HR_COLOR_DARK else KhetiBlocks.HR_COLOR_LIGHT
    Column(modifier.fillMaxWidth()) {
        Spacer(Modifier.height(KhetiBlocks.HR_MARGIN_BLOCK.dp))
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .fillMaxWidth(KhetiBlocks.HR_WIDTH_FRACTION)
                    .height(KhetiBlocks.HR_HEIGHT.dp)
                    .background(color),
            )
        }
        Spacer(Modifier.height((KhetiBlocks.HR_MARGIN_BLOCK - KhetiBlocks.HR_HEIGHT).dp))
    }
}

/**
 * 代码块 `pre`：等宽字体 + 浅色背景 + 内边距，**不做赫蹏间距处理**
 * （上游 `HETI_SKIPPED_ELEMENTS` 明确跳过 `pre` 与 `code`）。
 */
@Composable
fun KhetiPre(
    text: String,
    modifier: Modifier = Modifier,
    dark: Boolean = false,
) {
    val colors = LocalKhetiColors.current
    Box(
        modifier
            .fillMaxWidth()
            .background(colors.ink.copy(alpha = KhetiBlocks.SURFACE_ALPHA))
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        BasicText(
            text = text,
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 14.sp,
                lineHeight = 21.sp,
                color = if (dark) Color(0xFFE8E6E0) else Color(0xFF1A1A1A),
            ),
        )
    }
}

/**
 * 列表：无序/有序（含中文序号 `heti-list-han` 与拉丁序号 `heti-list-latin`）。
 *
 * 标记由 [khetiListMarker] 生成并作为文本前缀 —— 上游用 `list-style-type`，
 * Compose 没有对应能力，这是等价实现（标记不参与悬挂/挤压，与上游一致）。
 */
@Composable
fun KhetiList(
    items: List<String>,
    modifier: Modifier = Modifier,
    marker: KhetiListMarker = KhetiListMarker.Bullet,
    flavor: KhetiFlavor = KhetiFlavor.Classic,
) {
    Column(modifier.fillMaxWidth()) {
        items.forEachIndexed { i, item ->
            KhetiText(
                text = khetiListMarker(i + 1, marker) + item,
                flavor = flavor,
                alignment = KhetiAlignment.Start,
                spacing = true,
            )
        }
    }
}

/**
 * 图 `figure`：整体居中（上游 `figure { display: block; text-align: center }`）。
 */
@Composable
fun KhetiFigure(
    modifier: Modifier = Modifier,
    caption: String? = null,
    content: @Composable () -> Unit,
) {
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        content()
        if (!caption.isNullOrEmpty()) KhetiFigcaption(caption)
    }
}

/**
 * 图注 `figcaption`：14px / 24px，**文字左对齐**但在 figure 内居中成块
 * （上游 `figcaption { display: inline-block; font-size: 14px; text-align: start }`）。
 */
@Composable
fun KhetiFigcaption(text: String, modifier: Modifier = Modifier) {
    val colors = LocalKhetiColors.current
    Box(Modifier.padding(top = 4.dp)) {
        BasicText(
            text = text,
            modifier = modifier,
            style = TextStyle(
                fontSize = KhetiBlocks.CAPTION_FONT_SIZE.sp,
                lineHeight = KhetiBlocks.CAPTION_LINE_HEIGHT.sp,
                textAlign = TextAlign.Start,
                color = colors.ink,
            ),
        )
    }
}

/**
 * 表格 `table`：1px 实线边框、单元格内边距 6/8、居中、表注在**下方**。
 *
 * 上游：`table-layout: fixed; border-collapse: collapse; margin-block 12/24; margin-inline auto`。
 */
@Composable
fun KhetiTable(
    header: List<String>,
    rows: List<List<String>>,
    modifier: Modifier = Modifier,
    caption: String? = null,
    dark: Boolean = false,
) {
    val colors = LocalKhetiColors.current
    val border = if (dark) KhetiBlocks.TABLE_BORDER_DARK else KhetiBlocks.TABLE_BORDER_LIGHT
    Column(
        modifier
            .fillMaxWidth()
            .padding(top = KhetiBlocks.TABLE_MARGIN_TOP.dp, bottom = KhetiBlocks.TABLE_MARGIN_BOTTOM.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(Modifier.border(1.dp, border)) {
            if (header.isNotEmpty()) {
                Row { header.forEach { KhetiTableCell(it, border, colors, true) } }
            }
            rows.forEach { row ->
                Row { row.forEach { KhetiTableCell(it, border, colors, false) } }
            }
        }
        if (!caption.isNullOrEmpty()) {
            Spacer(Modifier.height(2.dp))
            BasicText(
                text = caption,
                style = TextStyle(
                    fontSize = KhetiBlocks.CAPTION_FONT_SIZE.sp,
                    lineHeight = KhetiBlocks.CAPTION_LINE_HEIGHT.sp,
                    color = colors.ink,
                ),
            )
        }
    }
}

/**
 * 脚注页脚（上游 `.heti-fn`）：距正文 59px、顶部 1px 分隔线、14px 黑体、24px 行高，
 * 列表顶边距 0.5 网格；[highlight]（1 起）对应上游 `li:target` 的高亮底色。
 *
 * 点击某条会回调 [onBack]（对应上游的 `^` 返回链接）。
 */
@Composable
fun KhetiFootnotes(
    notes: List<String>,
    modifier: Modifier = Modifier,
    highlight: Int? = null,
    onBack: ((Int) -> Unit)? = null,
    dark: Boolean = false,
) {
    val colors = LocalKhetiColors.current
    val fonts = LocalKhetiFontFamilies.current
    val border = if (dark) KhetiBlocks.FOOTNOTE_BORDER_DARK else KhetiBlocks.FOOTNOTE_BORDER_LIGHT
    val hl = if (dark) KhetiBlocks.FOOTNOTE_HIGHLIGHT_DARK else KhetiBlocks.FOOTNOTE_HIGHLIGHT_LIGHT
    val textStyle = TextStyle(
        fontSize = KhetiBlocks.FOOTNOTE_FONT_SIZE.sp,
        lineHeight = KhetiBlocks.FOOTNOTE_LINE_HEIGHT.sp,
        fontFamily = fonts.hei,
        color = colors.ink,
    )
    Column(modifier.fillMaxWidth()) {
        Spacer(Modifier.height(KhetiBlocks.FOOTNOTE_MARGIN_TOP.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(border),
        )
        Spacer(Modifier.height(KhetiBlocks.FOOTNOTE_LIST_MARGIN_TOP.dp))
        notes.forEachIndexed { i, note ->
            val n = i + 1
            Box(
                Modifier
                    .fillMaxWidth()
                    .then(if (highlight == n) Modifier.background(hl) else Modifier)
                    .then(if (onBack != null) Modifier.clickable { onBack(n) } else Modifier),
            ) {
                BasicText(
                    text = khetiListMarker(n, KhetiListMarker.Decimal) + note,
                    style = textStyle,
                )
            }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.KhetiTableCell(
    text: String,
    border: Color,
    colors: KhetiColors,
    isHeader: Boolean,
) {
    Box(
        Modifier
            .weight(1f)
            .border(1.dp, border)
            .padding(
                horizontal = KhetiBlocks.TABLE_CELL_PADDING_INLINE.dp,
                vertical = KhetiBlocks.TABLE_CELL_PADDING_BLOCK.dp,
            ),
    ) {
        BasicText(
            text = text,
            style = TextStyle(
                fontSize = KhetiMetrics.FONT_SIZE_NORMAL.sp,
                lineHeight = KhetiMetrics.GRID_UNIT.sp,
                color = colors.ink,
                fontWeight = androidx.compose.ui.text.font.FontWeight(
                    if (isHeader) KhetiBlocks.HEADING_WEIGHT else KhetiMetrics.WEIGHT_NORMAL,
                ),
            ),
        )
    }
}
