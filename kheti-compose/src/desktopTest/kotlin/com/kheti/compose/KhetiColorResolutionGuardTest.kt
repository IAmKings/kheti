package com.kheti.compose

import androidx.compose.ui.graphics.Color
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * **颜色解析守卫**（与 `NoPlatformLetterSpacingGuardTest` 同类）。
 *
 * 背景：连续三处真机缺陷根因同类 —— 颜色未显式解析或解析层级错误：
 * 1. `KhetiText` 传 `Color.Unspecified` → 绘制层回退平台默认色（黑）→ **深色模式整片空白**；
 * 2. 示例页原生 `Material3 Text` 同理（`MaterialTheme` 只在 `Surface` 内适配内容色）；
 * 3. 图注/表注/小节标签被涂成 `inkSecondary`（#6B6B6B）→ 浅色模式下过灰难读，
 *    且**偏离上游**（`heti.min.css` 中 `caption`/`figcaption` 均无 color 声明，应继承正文色）。
 *
 * 本测试用两种手段防回归：**纯函数语义**（明/暗主题）+ **源码白名单扫描**。
 */
class KhetiColorResolutionGuardTest {

    private val themes = listOf("light" to KhetiLightColors, "dark" to KhetiDarkColors)

    private fun composeRoot() = File("../kheti-compose/src/commonMain/kotlin/com/kheti/compose")

    @Test
    fun 未指定颜色必须解析为主题的正文色() {
        for ((name, colors) in themes) {
            assertEquals(
                colors.ink,
                resolveKhetiColor(Color.Unspecified, colors),
                "$name 主题：Color.Unspecified 应解析为 ink（否则深色模式下不可见）",
            )
            assertNotEquals(
                Color.Unspecified,
                resolveKhetiColor(Color.Unspecified, colors),
                "$name 主题：解析结果不得仍是 Unspecified",
            )
        }
    }

    @Test
    fun 显式颜色优先且明暗解析结果不同() {
        val explicit = Color(0xFF123456)
        for ((name, colors) in themes) {
            assertEquals(explicit, resolveKhetiColor(explicit, colors), "$name 主题：显式色应优先")
        }
        // 若两主题解析结果相同，说明某处硬编码了单主题颜色
        assertNotEquals(
            resolveKhetiColor(Color.Unspecified, KhetiLightColors),
            resolveKhetiColor(Color.Unspecified, KhetiDarkColors),
            "明暗主题的解析结果必须不同",
        )
    }

    @Test
    fun 各文本组件都必须显式解析未指定颜色() {
        val root = composeRoot()
        assertTrue(root.exists(), "找不到源码目录 ${root.absolutePath}")
        // 真机上因此出过两个 bug：KhetiText 漏解析
        for (name in listOf("KhetiText.kt", "KhetiVerticalText.kt", "KhetiColumns.kt")) {
            val f = File(root, name)
            assertTrue(f.exists(), "缺少 $name")
            val src = f.readText()
            assertTrue(
                src.contains("resolveKhetiColor") || src.contains("Color.Unspecified"),
                "$name 未显式解析 Color.Unspecified —— 深色模式下会回退成平台默认黑",
            )
        }
        // KhetiPoem 系列始终显式传主题色
        assertTrue(File(root, "KhetiPoem.kt").readText().contains("colors.ink"))
        assertTrue(File(root, "KhetiVerticalPoem.kt").readText().contains("colors.ink"))
    }

    @Test
    fun 次级色只能用于诗词元信息不得用于图注表注() {
        val roots = listOf(
            composeRoot(),
            File("../kheti-layout/src/commonMain/kotlin/com/kheti/layout"),
        )
        // 白名单：
        // - KhetiTheme.kt：主题定义本身
        // - KhetiPoem.kt / KhetiVerticalPoem.kt：诗词元信息（对应上游 `.heti-meta`）
        // 上游 caption/figcaption/正文**均不改色**，因此其它文件出现 inkSecondary 即视为退化。
        val allow = setOf("KhetiTheme.kt", "KhetiPoem.kt", "KhetiVerticalPoem.kt")
        val offenders = ArrayList<String>()
        for (root in roots) {
            if (!root.exists()) continue
            root.walkTopDown().filter { it.extension == "kt" }.forEach { f ->
                if (f.name in allow) return@forEach
                f.readLines().forEachIndexed { i, line ->
                    if (line.contains("inkSecondary")) offenders += "${f.name}:${i + 1}: ${line.trim()}"
                }
            }
        }
        assertTrue(
            offenders.isEmpty(),
            "inkSecondary 仅允许用于诗词元信息（上游 caption/figcaption 无 color，应为正文色）：\n" +
                offenders.joinToString("\n"),
        )
    }
}
