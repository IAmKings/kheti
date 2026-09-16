package com.kheti.layout

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.TextUnit
import com.kheti.KhetiMetrics
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * **ADR-001 承诺的守卫测试**：渲染/度量路径禁止使用平台的 `letterSpacing`。
 *
 * 为什么必须有：kheti 的全部间距都由引擎自绘施加。一旦有人在渲染路径上改用
 * `TextStyle.letterSpacing`，就会悄悄退回平台语义 —— 而 Phase 0 实测证明
 * Skia 与 Minikin 对该属性的分配方式不同（Skia 尾侧 X、Android X/2），
 * 于是跨平台几何等价会**无声地**失效，普通用例还照样"看起来对"。
 *
 * 本测试从两侧设防：
 * 1. 运行时：kheti 产出的 TextStyle 不得携带 letterSpacing；
 * 2. 源码：渲染/度量路径不得出现 `letterSpacing =` 赋值（`letterSpacingEm` 是自有字段，不算）。
 */
class NoPlatformLetterSpacingGuardTest {

    @Test
    fun 运行时kheti样式不得携带平台字距() {
        for (size in KhetiMetrics.Size.entries) {
            val ts = KhetiTextStyle.of(size).toTextStyle()
            assertEquals(
                TextUnit.Unspecified,
                ts.letterSpacing,
                "字号档 $size 的 TextStyle 携带了 letterSpacing —— 必须由引擎自绘施加",
            )
        }
        val ts = KhetiTextStyle.of(KhetiMetrics.Size.Normal, FontFamily.Serif).toTextStyle()
        assertEquals(TextUnit.Unspecified, ts.letterSpacing)
    }

    @Test
    fun 源码中渲染路径不得出现平台字距赋值() {
        val roots = listOf(
            File("../kheti-layout/src/commonMain/kotlin/com/kheti/layout"),
            File("../kheti-compose/src/commonMain/kotlin/com/kheti/compose"),
            File("../kheti-core/src/commonMain/kotlin/com/kheti"),
        )
        val assign = Regex("""\bletterSpacing\s*=""")
        // 例外：Spike.kt 是 Phase 0 的**测量探针**，其目的正是量出平台 letterSpacing 的分配方式，
        // 因此它必须使用该属性。它是证据代码，不参与渲染路径。
        val allowlist = setOf("Spike.kt")
        val offenders = ArrayList<String>()
        for (root in roots) {
            if (!root.exists()) continue
            root.walkTopDown().filter { it.extension == "kt" }.forEach { f ->
                if (f.name in allowlist) return@forEach
                f.readLines().forEachIndexed { i, line ->
                    // `letterSpacingEm = …` 是 kheti 自有字段（em 值），不在此列
                    if (assign.containsMatchIn(line) && !line.contains("letterSpacingEm")) {
                        offenders += "${f.name}:${i + 1}: ${line.trim()}"
                    }
                }
            }
        }
        assertTrue(
            offenders.isEmpty(),
            "渲染路径出现平台 letterSpacing 赋值（会破坏跨平台几何等价）：\n" + offenders.joinToString("\n"),
        )
    }
}
