package com.kheti

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import java.io.File

/**
 * 差分等价性测试：Kotlin 移植 vs Node 端用**上游逐字正则**独立实现的金标准。
 *
 * 金标准由 `tools/reference-gen/heti-golden.mjs` 生成（`reference/heti-golden.tsv`）。
 * 若本测试失败，说明 Kotlin 端误解了赫蹏的规则——**必须先改 Kotlin，不得改金标准**。
 */
class GoldenEquivalenceTest {

    private fun goldenFile(): File {
        val candidates = listOf(
            File("../reference/heti-golden.tsv"),
            File("reference/heti-golden.tsv"),
            File("../../reference/heti-golden.tsv"),
        )
        return candidates.firstOrNull { it.exists() }
            ?: error("找不到金标准文件，候选路径：${candidates.map { it.absolutePath }}")
    }

    private fun floats(csv: String): List<Float> =
        if (csv.isBlank()) emptyList() else csv.split(',').map { it.toFloat() }

    @Test
    fun 与赫蹏上游正则的金标准逐字符一致() {
        val lines = goldenFile().readLines().filter { it.isNotBlank() }
        assertTrue(lines.size >= 20, "金标准样本过少：${lines.size}")

        var checked = 0
        for ((i, line) in lines.withIndex()) {
            val cols = line.split('\t')
            assertEquals(5, cols.size, "第 $i 行格式错误：$line")
            val sourceText = cols[0]
            val expectedText = cols[1]
            val expectedLead = floats(cols[2])
            val expectedTrail = floats(cols[3])
            val expectedRemoved = cols[4].toInt()

            val actual = AdjustmentPlanner.plan(sourceText)

            assertEquals(expectedText, actual.text, "归一化文本不一致（第 $i 行：$sourceText）")
            assertEquals(expectedLead, actual.leadingEm, "leading 增量不一致：$sourceText")
            assertEquals(expectedTrail, actual.trailingEm, "trailing 增量不一致：$sourceText")
            assertEquals(expectedRemoved, actual.removedSpaces, "吞掉空格数不一致：$sourceText")
            checked++
        }
        assertTrue(checked >= 20, "实际比对样本数 $checked")
    }
}
