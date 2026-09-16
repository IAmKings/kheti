package com.kheti

/**
 * `q` 元素的引号处理（对应上游 `q { quotes: … }` 与 `::before/::after` 自动加引号）。D13 收尾。
 *
 * 两个上游要点：
 * 1. 引号集合由 [QuotePolicy] 决定（`cn` = 弯引号 `“”`／`tw`、`common` = 直角引号 `「」`）；
 * 2. **竖排时改用竖排引号集合**（上游 `--vertical q { quotes: <vertical 集合> }`），
 *    因此同一个 `q` 在横排与竖排下会插入不同字符。
 *
 * 嵌套按 CSS `quotes` 语义取层级：第 1 层用主引号、第 2 层用次引号，第 3 层再回主引号。
 *
 * ⚠️ 调用顺序：本函数会**改变文本与下标**，因此应在其它区间（ruby/着重号/行内样式）
 * 之前调用，并用 [QuotedText.mapIndex] 把它们映射到新文本上。
 */
data class QuotedText(
    val text: String,
    /** 每个插入点：在该原文下标**之前**插入了字符（升序）。 */
    private val insertions: List<Int>,
) {
    /** 把原文下标映射为新文本下标。 */
    fun mapIndex(originalIndex: Int): Int =
        originalIndex + insertions.count { it <= originalIndex }
}

/**
 * 给 [ranges] 指定的区间（原文下标，可嵌套）自动加上引号字符。
 *
 * @param ranges 每个 `q` 覆盖的区间（含首尾）；**可嵌套**，嵌套顺序不影响结果。
 * @param flow 书写方向：竖排使用竖排引号集合。
 */
fun wrapQuotes(
    text: String,
    ranges: List<IntRange>,
    policy: QuotePolicy = QuotePolicy.DEFAULT,
    flow: KhetiFlow = KhetiFlow.Horizontal,
): QuotedText {
    if (ranges.isEmpty()) return QuotedText(text, emptyList())

    val openPrimary: String
    val closePrimary: String
    val openSecondary: String
    val closeSecondary: String
    if (flow == KhetiFlow.Vertical) {
        openPrimary = policy.verticalPrimary
        closePrimary = policy.verticalSecondary
        openSecondary = policy.verticalOpenSecondary.ifEmpty { policy.verticalPrimary }
        closeSecondary = policy.verticalCloseSecondary.ifEmpty { policy.verticalSecondary }
    } else {
        openPrimary = policy.horizontalPrimary
        closePrimary = policy.horizontalSecondary
        openSecondary = policy.horizontalOpenSecondary.ifEmpty { policy.horizontalPrimary }
        closeSecondary = policy.horizontalCloseSecondary.ifEmpty { policy.horizontalSecondary }
    }

    // 每个区间计算嵌套深度（被多少个其它区间包含）
    fun depth(r: IntRange): Int = ranges.count { it != r && it.first <= r.first && it.last >= r.last }

    // 插入点：区间首之前插开引号，区间尾之后插闭引号
    data class Ins(val at: Int, val s: String)

    val ins = ArrayList<Ins>()
    for (r in ranges) {
        if (r.last < r.first) continue
        val d = depth(r)
        val (o, c) = if (d % 2 == 0) openPrimary to closePrimary else openSecondary to closeSecondary
        ins += Ins(r.first, o)
        ins += Ins(r.last + 1, c)
    }
    // 同一插入点按开引号在前（多重嵌套同点时保持稳定顺序）
    ins.sortWith(compareBy({ it.at }, { if (it.s == closePrimary || it.s == closeSecondary) 1 else 0 }))

    val sb = StringBuilder(text)
    val insertions = ArrayList<Int>()
    // 从后往前插入，避免下标漂移
    for (i in ins.indices.reversed()) {
        val it = ins[i]
        val pos = it.at.coerceIn(0, sb.length)
        sb.insert(pos, it.s)
        insertions += it.at
    }
    insertions.sort()
    return QuotedText(sb.toString(), insertions)
}
