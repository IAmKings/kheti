package com.kheti.layout

import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.kheti.KhetiMetrics
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * D11 多栏（上游 `--columns-N`）：把超高单栏按 `column-fill: balance` 切成 N 栏。
 *
 * 断言的是**分栏语义**而不是像素：行数均衡、行不跨栏、栏位定位正确。
 */
class M8MultiColumnTest {

    private fun engine(): KhetiEngine =
        KhetiEngine(
            TextMeasurer(createFontFamilyResolver(), Density(1f), LayoutDirection.Ltr),
            Density(1f),
        )

    private fun family(): FontFamily {
        val f = File("../reference/fonts/lxgw_neozhisong_screen.ttf")
        return if (f.exists()) FontFamily(androidx.compose.ui.text.platform.Font(f)) else FontFamily.Serif
    }

    /** 足够长的文本，保证排成多行 */
    private val text = "先帝创业未半而中道崩殂，今天下三分，益州疲弊，此诚危急存亡之秋也。" +
        "然侍卫之臣不懈于内，忠志之士忘身于外者，盖追先帝之殊遇，欲报之于陛下也。" +
        "诚宜开张圣听，以光先帝遗德，恢弘志士之气，不宜妄自菲薄，引喻失义。"

    @Test
    fun 按栏数均衡分栏且行不跨栏() {
        val style = KhetiTextStyle.of(KhetiMetrics.Size.Normal, family())
        val base = engine().layout(text, style, maxWidthPx = 320)
        assertTrue(base.lineCount >= 6, "应排成多行才能分栏，实际 ${base.lineCount}")

        val two = splitIntoColumns(base, count = 2, gapPx = 24f)
        assertEquals(2, two.columns.size)
        val counts = two.columns.map { it.lineCount }
        assertTrue(
            (counts.max() - counts.min()) <= 1,
            "column-fill: balance 语义下每栏行数差应 ≤ 1，实际 $counts",
        )
        // 行不重复、不丢失：各栏首尾相接
        val covered = two.columns.sumOf { it.lineCount }
        assertEquals(base.lineCount, covered, "所有行都应被分到某一栏")

        // 每栏首行 top 归零
        for ((k, col) in two.columns.withIndex()) {
            assertEquals(0f, col.lines.first().top, 0.01f, "第 $k 栏首行 top 应归零")
        }

        // 栏宽 = 原行宽；整幅宽 = 2 栏 + 1 个间距
        assertEquals(base.size.width, two.columnWidthPx)
        assertEquals(
            (base.size.width * 2 + 24f).toInt(),
            two.size.width,
            "整幅宽应为 2 栏宽 + 1 间距",
        )
        // 栏高 = 最多行的那一栏
        assertEquals((counts.max() * base.lineHeightPx).toInt(), two.size.height)

        println(">>> D11 columns-2：共 ${base.lineCount} 行 → 各栏 $counts 行；栏宽 ${base.size.width}px，整幅 ${two.size.width}px")
    }

    @Test
    fun 三栏与四栏同样均衡() {
        val style = KhetiTextStyle.of(KhetiMetrics.Size.Normal, family())
        val base = engine().layout(text, style, maxWidthPx = 260)
        for (n in listOf(3, 4)) {
            val m = splitIntoColumns(base, count = n, gapPx = 16f)
            assertEquals(n, m.columns.size, "$n 栏应产出 $n 栏")
            val counts = m.columns.map { it.lineCount }
            assertTrue(
                (counts.max() - counts.min()) <= 1,
                "$n 栏行数应均衡，实际 $counts",
            )
            println(">>> D11 columns-$n：各栏 $counts 行")
        }
    }

    @Test
    fun 按栏宽求栏数与上游columns_Nem语义一致() {
        // 容器 800px，栏宽 200px，间距 20px → (800+20)/(200+20) = 3 栏
        assertEquals(3, columnCountForWidth(800, 200f, 20f))
        // 容器放不下 2 栏时退化为 1 栏
        assertEquals(1, columnCountForWidth(200, 200f, 20f))
        // 恰好整除
        assertEquals(4, columnCountForWidth(860, 200f, 20f))
        // 非法栏宽兜底
        assertEquals(1, columnCountForWidth(800, 0f, 20f))
    }

    @Test
    fun 单栏请求退化为原布局() {
        val style = KhetiTextStyle.of(KhetiMetrics.Size.Normal, family())
        val base = engine().layout(text, style, maxWidthPx = 320)
        val one = splitIntoColumns(base, count = 1, gapPx = 24f)
        assertEquals(1, one.columns.size)
        assertEquals(base.lineCount, one.columns[0].lineCount)
        assertEquals(0f, one.gapPx, "单栏不应引入间距")
    }
}
