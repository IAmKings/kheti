package com.kheti

/**
 * 赫蹏「中西文混排 + 全角标点挤压」规则的纯 Kotlin 移植。
 *
 * ## 与上游的等价性
 * 上游 `js/heti-addon.js` 用 5 趟 DOM 查找替换实现（顺序即语义）：
 * 1. `REG_FULL`  CJK + 西文串 + CJK → 包裹 `heti-spacing-start heti-spacing-end`（两侧各 ¼ em）
 * 2. `REG_START` 行首西文串 + CJK    → `heti-spacing-start`（尾侧 ¼ em）
 * 3. `REG_END`   CJK + 行尾西文串    → `heti-spacing-end`（首侧 ¼ em）
 * 4. `REG_ADJ_HALF`    停顿号接括号类等 → `heti-adjacent-half`（−½ em）
 * 5. `REG_ADJ_QUARTER` 间隔符接开括号等 → `heti-adjacent-quarter`（−¼ em）
 * 6. 弯引号特例（同一 quarter 类）      → −¼ em
 *
 * 本实现保持**同一顺序与同一优先级**（FULL 命中后其区间被后续趟次跳过，等价于上游把命中内容
 * 包进 `<heti-spacing>` 后被 `HETI_SKIPPED_ELEMENTS` 过滤）。由于 4/5/6 三趟的触发模式在同一
 * 字符上互斥（见 commonTest 的穷举测试），单遍扫描与上游多趟结果一致。
 *
 * 等价性由 `AdjustmentPlannerGoldenTest` 用 **Node 运行上游原始正则**生成的金标准逐字符校验。
 */
object AdjustmentPlanner {

    /** 中西文间距：¼ 字宽（上游 `margin-inline-end: 0.25em`）。 */
    const val GAP_EM: Float = 0.25f

    /** 标点挤压：½ 字宽（上游 `heti-adjacent-half`）。 */
    const val COMPRESS_HALF_EM: Float = -0.5f

    /** 标点挤压：¼ 字宽（上游 `heti-adjacent-quarter`）。 */
    const val COMPRESS_QUARTER_EM: Float = -0.25f

    private val REG_FULL = Regex(
        "(?<=[${HetiChars.CJK}])( *[${HetiChars.ANS}]+(?: +[${HetiChars.ANS}]+)* *)(?=[${HetiChars.CJK}])"
    )
    private val REG_START = Regex(
        "([${HetiChars.ANS}]+(?: +[${HetiChars.ANS}]+)* *)(?=[${HetiChars.CJK}])"
    )
    private val REG_END = Regex(
        "(?<=[${HetiChars.CJK}])( *[${HetiChars.ANS}]+(?: +[${HetiChars.ANS}]+)*)"
    )
    private val REG_ADJ_HALF = Regex(
        "([${HetiChars.BD_STOP}])(?=[${HetiChars.BD_START}])" +
            "|([${HetiChars.BD_OPEN}])(?=[${HetiChars.BD_OPEN}])" +
            "|([${HetiChars.BD_CLOSE}])(?=[${HetiChars.BD_END}])"
    )
    private val REG_ADJ_QUARTER = Regex(
        "([${HetiChars.BD_SEP}])(?=[${HetiChars.BD_OPEN}])" +
            "|([${HetiChars.BD_CLOSE}])(?=[${HetiChars.BD_SEP}])"
    )
    private val REG_ADJ_QUARTER_QUOTE = Regex(
        "([${HetiChars.BD_STOP}])(?=[${HetiChars.BD_HALF_START}])" +
            "|([${HetiChars.BD_HALF_OPEN}])(?=[${HetiChars.BD_OPEN}])"
    )

    fun plan(text: String): PlannedText {
        val n = text.length
        val leading = FloatArray(n)
        val trailing = FloatArray(n)
        val trim = BooleanArray(n)
        val assigned = BooleanArray(n)
        val inGap = BooleanArray(n)

        // ---- 1. 中西文间距（FULL → START → END，先到先得）----
        fun applyGap(m: MatchResult, trimLead: Boolean, trimTrail: Boolean, addLead: Boolean, addTrail: Boolean) {
            val g = m.groups[1]?.range ?: return
            for (i in g) if (assigned[i]) return // 已被 FULL 命中：后续趟次跳过（等价上游 skip）
            var s = g.first
            var e = g.last
            if (trimLead) while (s <= e && text[s] == ' ') { trim[s] = true; s++ }
            if (trimTrail) while (e >= s && text[e] == ' ') { trim[e] = true; e-- }
            if (s > e) return
            // 边距是**单侧**的：heti-spacing-start 只有 margin-inline-end（尾），
            // heti-spacing-end 只有 margin-inline-start（首）。
            if (addLead) leading[s] += GAP_EM
            if (addTrail) trailing[e] += GAP_EM
            for (i in s..e) inGap[i] = true
            for (i in g) assigned[i] = true
        }

        // 顺序即语义：FULL（CJK+西文+CJK，两侧）→ START（行首西文，尾侧）→ END（行尾西文，首侧）
        for (m in REG_FULL.findAll(text)) {
            applyGap(m, trimLead = true, trimTrail = true, addLead = true, addTrail = true)
        }
        for (m in REG_START.findAll(text)) {
            applyGap(m, trimLead = false, trimTrail = true, addLead = false, addTrail = true)
        }
        for (m in REG_END.findAll(text)) {
            applyGap(m, trimLead = true, trimTrail = false, addLead = true, addTrail = false)
        }

        // ---- 2. 标点挤压（三趟互斥，单遍即可）----
        val compressed = BooleanArray(n)
        fun applyCompression(re: Regex, em: Float) {
            for (m in re.findAll(text)) {
                val i = m.range.first
                if (compressed[i] || assigned[i]) continue
                trailing[i] += em
                compressed[i] = true
            }
        }
        applyCompression(REG_ADJ_HALF, COMPRESS_HALF_EM)
        applyCompression(REG_ADJ_QUARTER, COMPRESS_QUARTER_EM)
        applyCompression(REG_ADJ_QUARTER_QUOTE, COMPRESS_QUARTER_EM)

        // ---- 3. 输出归一化文本与逐字增量 ----
        val sb = StringBuilder(n)
        val outLead = ArrayList<Float>(n)
        val outTrail = ArrayList<Float>(n)
        val gaps = mutableListOf<IntRange>()
        val trimmed = mutableListOf<Int>()
        var removed = 0
        var gapStart = -1
        for (i in 0 until n) {
            if (trim[i]) {
                removed++
                trimmed += i
                continue
            }
            val outIndex = sb.length
            if (inGap[i]) {
                if (gapStart < 0) gapStart = outIndex
            } else if (gapStart >= 0) {
                gaps += gapStart until outIndex
                gapStart = -1
            }
            sb.append(text[i])
            outLead.add(leading[i])
            outTrail.add(trailing[i])
        }
        if (gapStart >= 0 && gapStart < sb.length) gaps += gapStart until sb.length
        return PlannedText(sb.toString(), outLead, outTrail, removed, trimmed, gaps)
    }

    /** 未做任何调整的恒等结果（`spacing = false` 时使用）。 */
    fun identity(text: String): PlannedText =
        PlannedText(text, List(text.length) { 0f }, List(text.length) { 0f }, 0, emptyList(), emptyList())
}
