package com.kheti

/** 单个字符上承载的间距调整类型。 */
enum class SpanKind {
    /** 中西文混排：CJK 与西文之间补 ¼ 字宽。 */
    CjkLatinGap,

    /** 全角标点挤压：½ 字宽或 ¼ 字宽。 */
    PunctCompression,
}

/**
 * 一段连续、间距调整相同的字符区间。
 *
 * [leadingEm] 加在该区间首字**之前**，[trailingEm] 加在该区间末字**之后**（单位：字宽 em）。
 * 正值对应赫蹏的 `margin-inline-start/end`，负值对应标点挤压的负外边距。
 */
data class InlineSpan(
    val start: Int,
    val end: Int,
    val leadingEm: Float,
    val trailingEm: Float,
    val kind: SpanKind,
)

/**
 * 经赫蹏规则归一化后的文本及其逐字间距增量。
 *
 * [text] 是**归一化文本**：中西文之间手敲的空格已被吞掉（等价于上游脚本替换 DOM 后的结果）。
 * [leadingEm]/[trailingEm] 与 [text] 逐字符对齐，是版式引擎唯一需要的输入——
 * 引擎把它们当作纯几何增量施加在度量结果上，不依赖任何平台的 `letterSpacing` 语义。
 */
data class PlannedText(
    val text: String,
    val leadingEm: List<Float>,
    val trailingEm: List<Float>,
    /** 被吞掉的空格数量（诊断与金标准比对用）。 */
    val removedSpaces: Int = 0,
    /** 被吞掉空格在**原文**中的下标（升序），用于把原文下标映射到归一化下标。 */
    val trimmedIndices: List<Int> = emptyList(),
    /**
     * 被赫蹏包进 `heti-spacing` 的西文串（归一化后的下标区间）。
     *
     * 用途：上游 `.heti heti-spacing { letter-spacing: normal }` —— 这些区间**不加** 0.02em 字距。
     * （字号为 x-large 时上游规则被更高优先级覆盖，全字距照加，见 `KhetiMetrics.Size`。）
     */
    val gapRanges: List<IntRange> = emptyList(),
) {
    init {
        require(leadingEm.size == text.length) { "leadingEm 必须与 text 等长" }
        require(trailingEm.size == text.length) { "trailingEm 必须与 text 等长" }
    }

    /** 按相同增量合并成区间（供富文本映射与调试）。 */
    fun spans(): List<InlineSpan> {
        val out = mutableListOf<InlineSpan>()
        var i = 0
        while (i < text.length) {
            val lead = leadingEm[i]
            val trail = trailingEm[i]
            if (lead == 0f && trail == 0f) {
                i++
                continue
            }
            var j = i
            while (j + 1 < text.length && leadingEm[j + 1] == lead && trailingEm[j + 1] == trail) j++
            val kind = if (lead < 0f || trail < 0f) SpanKind.PunctCompression else SpanKind.CjkLatinGap
            out += InlineSpan(i, j, lead, trail, kind)
            i = j + 1
        }
        return out
    }

    /** 整行因调整产生的净增量（em）。 */
    fun totalDeltaEm(): Float = leadingEm.sum() + trailingEm.sum()

    /**
     * 把**原文**下标映射为归一化文本下标。
     *
     * 行间注（ruby）等区间由调用方按原文给出，而归一化吞掉了中西文之间的空格，
     * 因此必须经此映射才能在版式中定位。
     */
    fun plannedIndex(originalIndex: Int): Int {
        if (trimmedIndices.isEmpty()) return originalIndex
        var removed = 0
        for (t in trimmedIndices) {
            if (t < originalIndex) removed++ else break
        }
        return originalIndex - removed
    }
}
