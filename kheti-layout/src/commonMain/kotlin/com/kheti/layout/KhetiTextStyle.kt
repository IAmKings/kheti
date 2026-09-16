package com.kheti.layout

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.kheti.KhetiMetrics

/**
 * kheti 文本样式。字号/行高用 `TextUnit`（遵循调用方 Density），
 * 字距与所有赫蹏增量由**引擎自绘施加**，不走 `TextStyle.letterSpacing`（见 ADR-001）。
 */
data class KhetiTextStyle(
    val fontSize: TextUnit = 16.sp,
    val lineHeight: TextUnit = 24.sp,
    /** 逐字字距（em）。赫蹏容器默认 0.02em；x-large 档为 0.05em。 */
    val letterSpacingEm: Float = KhetiMetrics.LETTER_SPACING_CJK_EM,
    val fontFamily: FontFamily = FontFamily.Default,
    val fontWeight: FontWeight = FontWeight.Normal,
    /** 赫蹏字号档，决定 [KhetiMetrics.Size.letterSpacingOverridesGap] 等细节。 */
    val size: KhetiMetrics.Size = KhetiMetrics.Size.Normal,
) {
    fun toTextStyle(): TextStyle = TextStyle(
        fontSize = fontSize,
        lineHeight = lineHeight,
        fontFamily = fontFamily,
        fontWeight = fontWeight,
    )

    /** 行盒高度（px）。 */
    fun lineHeightPx(density: androidx.compose.ui.unit.Density): Float =
        with(density) { lineHeight.toPx() }

    fun fontSizePx(density: androidx.compose.ui.unit.Density): Float =
        with(density) { fontSize.toPx() }

    companion object {
        /** 按赫蹏字号档构造（上游 `heti-x-large` 等 helper class 的等价物）。 */
        fun of(
            size: KhetiMetrics.Size,
            fontFamily: FontFamily = FontFamily.Default,
            fontWeight: FontWeight = FontWeight.Normal,
        ): KhetiTextStyle = KhetiTextStyle(
            fontSize = size.fontSize.sp,
            lineHeight = size.lineHeightPx.sp,
            letterSpacingEm = size.letterSpacingEm,
            fontFamily = fontFamily,
            fontWeight = fontWeight,
            size = size,
        )

        /** 按标题档构造（上游 `h1`~`h6`）。 */
        fun heading(
            level: KhetiMetrics.Heading,
            fontFamily: FontFamily = FontFamily.Default,
            fontWeight: FontWeight = FontWeight(800),
        ): KhetiTextStyle = KhetiTextStyle(
            fontSize = level.fontSize.sp,
            lineHeight = level.lineHeightPx.sp,
            letterSpacingEm = KhetiMetrics.LETTER_SPACING_CJK_EM,
            fontFamily = fontFamily,
            fontWeight = fontWeight,
            size = KhetiMetrics.Size.Normal,
        )
    }
}

/** 行内对齐方式（对应上游 `text-align`）。 */
enum class KhetiAlignment {
    Start,
    /** 诗词版式：居中、无首行缩进。 */
    Center,
    End,

    /** 散文版式：`text-align: justify`，CJK 按**字间**分配余量（平台只拉空格，故必须自研）。 */
    Justify,
}

/** 标点悬挂（上游 `heti-hang`：绝对定位，不占行宽）。 */
enum class KhetiHang {
    Off,

    /** 行末可悬挂标点不占行宽，绘制时溢出到行尾之外。 */
    LineEnd,
}

/**
 * 容器语言（对应上游 `.heti:lang(zh)` 与 `[lang="en-US"]` 的字距规则）。D15。
 *
 * 上游：中文容器 `letter-spacing: .02em`；非中文容器
 * （`:not(:lang(zh)):not(:lang(ja)):not(:lang(ko))`）字距归零、对齐改为 `start`。
 * 此前 kheti 恒用 0.02em，纯西文内容会偏松。
 */
enum class KhetiLang {
    /** 中文/日文/韩文：CJK 字距 0.02em。 */
    Zh,

    /** 西文容器：字距为 0。 */
    Latin,
}
