package com.kheti.layout

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntSize
import com.kheti.PlannedText
import kotlin.math.abs

/** 一段连续、可一次性绘制的字符（段内无间距调整，保留原生 shaping）。 */
data class KhetiSegment(val start: Int, val end: Int, val x: Float)

/** 一行（横排）的几何结果。 */
class KhetiLine(
    /** 归一化文本中的字符区间 [start, end)。 */
    val start: Int,
    val end: Int,
    /** 行盒顶部（y）。 */
    val top: Float,
    /** 行内容宽度（不含悬挂标点）。 */
    val width: Float,
    /** 对齐后行内容起点（x）。 */
    val x: Float,
    val segments: List<KhetiSegment>,
    /** 每个字符相对行起点的 x（长度 = end - start）。 */
    val charX: FloatArray,
    /** 每个字符的 advance（长度 = end - start）。 */
    val charAdvance: FloatArray,
    /** 被悬挂的字符下标（绝对值），-1 表示无。 */
    val hangingIndex: Int,
    /** 落在本行的注音（行间注）。 */
    val ruby: List<KhetiRubyGlyph> = emptyList(),
) {
    val length: Int get() = end - start
}

/**
 * 自绘布局结果。
 *
 * 与 `TextLayoutResult` 的差异：这里的一切几何都是 kheti 施加赫蹏增量**之后**的结果，
 * 且跨平台一致（见 `docs/phase0-evidence.md`）。
 */
class KhetiLayout(
    /** 归一化文本（中西文之间的手敲空格已被吞掉）。 */
    val text: String,
    val lines: List<KhetiLine>,
    val size: IntSize,
    val plan: PlannedText,
    /** 行盒高度（px）——赫蹏的垂直网格单位。 */
    val lineHeightPx: Float,
    /** 行内元素效果（无则 null）：底色/下划线/删除线等。 */
    val inline: KhetiInlineEffects? = null,
) {
    val lineCount: Int get() = lines.size

    /** 命中测试：返回最接近的字符下标。 */
    fun getOffsetForPosition(position: Offset): Int {
        if (lines.isEmpty()) return 0
        val line = lines.firstOrNull { position.y < it.top + lineHeightPx } ?: lines.last()
        val local = position.x - line.x
        var best = line.start
        var bestDist = Float.MAX_VALUE
        for (k in 0 until line.length) {
            val cx = line.charX[k]
            val dist = abs(local - cx)
            if (dist < bestDist) {
                bestDist = dist
                best = line.start + k
            }
        }
        return best
    }

    /** 字符包围盒（行盒高度）。 */
    fun getBoundingBox(offset: Int): Rect {
        val line = lines.firstOrNull { offset in it.start until it.end } ?: return Rect.Zero
        val k = offset - line.start
        val x = line.x + line.charX[k]
        return Rect(x, line.top, x + line.charAdvance[k], line.top + lineHeightPx)
    }

    /** 行盒（用于网格对齐校验）。 */
    fun getLineTop(lineIndex: Int): Float = lines.getOrNull(lineIndex)?.top ?: 0f
}
