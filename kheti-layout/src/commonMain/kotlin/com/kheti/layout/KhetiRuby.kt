package com.kheti.layout

/**
 * 行间注（对应上游 `<ruby>`）：把 [annotation] 注在**原文** `[start, end)` 的字符上。
 *
 * 下标基于调用方传入的原文；引擎会按归一化（吞掉中西文之间的空格）后的下标重新定位，
 * 因此调用方无需关心赫蹏的文本归一化。
 */
data class KhetiRuby(val start: Int, val end: Int, val annotation: String) {
    init {
        require(end > start) { "ruby 区间必须非空：[$start, $end)" }
    }
}

/**
 * 行间注版式（上游两种模式）：
 *
 * - [Inline]：`.heti-ruby--inline` —— 注音挤进行盒内，**不改变行高**，保持 1.5 网格。
 * - [Block]：`.heti--annotation` —— 注音占行盒上方，配合 2.25 倍行高、段间距 0、首行缩进 2em。
 */
enum class KhetiRubyMode { Inline, Block }

/** 已定位的注音（绘制层直接使用）。 */
class KhetiRubyGlyph(
    /** 基文在归一化文本中的区间（已裁剪到本行）。 */
    val baseStart: Int,
    val baseEnd: Int,
    val annotation: String,
    /** 注音左边界（画布坐标，含行对齐偏移）。 */
    val x: Float,
    /** 注音行盒顶部。 */
    val y: Float,
    val width: Float,
    val fontSizePx: Float,
)
