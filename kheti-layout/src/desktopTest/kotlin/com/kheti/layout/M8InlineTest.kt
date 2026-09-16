package com.kheti.layout

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.kheti.KhetiMetrics
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * M8 前两项的验证：
 * - **D15** 容器语言：西文容器字距归零（上游 `[lang=en-US]{letter-spacing:normal}`）
 * - **D13** 行内元素：区间 → 逐字符效果（下划线/删除线/点线/底色/斜体），并真实渲染
 */
class M8InlineTest {

    private fun family(): FontFamily {
        val f = File("../reference/fonts/lxgw_neozhisong_screen.ttf")
        return if (f.exists()) FontFamily(androidx.compose.ui.text.platform.Font(f)) else FontFamily.Serif
    }

    private fun engine(): KhetiEngine =
        KhetiEngine(
            TextMeasurer(createFontFamilyResolver(), Density(1f), LayoutDirection.Ltr),
            Density(1f),
        )

    // ------------------------------------------------------------- D15

    @Test
    fun 西文容器字距归零而中文容器保留零点零二em() {
        val style = KhetiTextStyle.of(KhetiMetrics.Size.Normal, family())
        val e = engine()
        val text = "abcabc"
        val zh = e.layout(text, style, maxWidthPx = 672, lang = KhetiLang.Zh)
        val latin = e.layout(text, style, maxWidthPx = 672, lang = KhetiLang.Latin)
        // 中文容器：每个字符后加 0.02em = 0.32px；西文容器：不加
        assertEquals(zh.lines[0].charX[1] - latin.lines[0].charX[1], 0.32f, 0.02f)
        // 行宽不含**末字**的 trailing，故 6 字只有 5 个间隙
        assertEquals(zh.lines[0].width - latin.lines[0].width, 5 * 0.32f, 0.05f)
        println(">>> D15：Zh 行宽=%.2f Latin 行宽=%.2f（差 %.2fpx = 6×0.02em）".format(zh.lines[0].width, latin.lines[0].width, zh.lines[0].width - latin.lines[0].width))
    }

    // ------------------------------------------------------------- D13

    @Test
    fun 行内区间解析为逐字符效果且经归一化映射正确() {
        val style = KhetiTextStyle.of(KhetiMetrics.Size.Normal, family())
        // 原文含中西文之间的手敲空格：归一化会吞掉，区间必须跟着平移
        val text = "中文 abc 赫蹏"
        val plan = com.kheti.AdjustmentPlanner.plan(text)
        assertEquals("中文abc赫蹏", plan.text)
        val layout = engine().layout(
            text = text,
            style = style,
            maxWidthPx = 672,
            inline = listOf(
                KhetiInlineSpan(text.indexOf('赫'), text.indexOf('赫') + 1, KhetiInlineKind.Mark),
                KhetiInlineSpan(text.indexOf('蹏'), text.indexOf('蹏') + 1, KhetiInlineKind.Proper),
            ),
        )
        val fx = assertNotNull(layout.inline, "应有行内效果")
        val he = plan.text.indexOf('赫')
        val ti = plan.text.indexOf('蹏')
        assertTrue(fx.background[he], "「赫」应被标记为底色（mark）")
        assertTrue(fx.underline[ti], "「蹏」应有下划线（专名号 u）")
        assertTrue(!fx.background[ti] && !fx.underline[he], "效果不应串到相邻字")
    }

    @Test
    fun 无行内区间时不产生效果表() {
        val style = KhetiTextStyle.of(KhetiMetrics.Size.Normal, family())
        val layout = engine().layout("中文", style, 672)
        assertNull(layout.inline, "无区间时 inline 应为 null（零开销）")
    }

    @Test
    fun 行内装饰真实渲染在正确位置() {
        val style = KhetiTextStyle.of(KhetiMetrics.Size.Normal, family())
        val m = TextMeasurer(createFontFamilyResolver(), Density(1f), LayoutDirection.Ltr)
        val e = KhetiEngine(m, Density(1f))
        val text = "中文标记测试"
        val spans = listOf(
            KhetiInlineSpan(2, 4, KhetiInlineKind.Mark),      // 标记：底色
            KhetiInlineSpan(4, 6, KhetiInlineKind.Proper),    // 专名号：下划线
        )
        val withFx = e.layout(text, style, 672, inline = spans)
        val plain = e.layout(text, style, 672)

        fun render(l: KhetiLayout): ImageBitmap {
            val pad = 10
            val w = l.size.width + pad * 2
            val h = l.size.height + pad * 2 + 10
            val bmp = ImageBitmap(w, h)
            CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(bmp), Size(w.toFloat(), h.toFloat())) {
                drawRect(color = Color.White)
                drawKhetiText(m, l, style, Color.Black, Density(1f), leftOffset = pad.toFloat(), topOffset = pad.toFloat())
                drawKhetiInlineLines(l, Color.Black, leftOffset = pad.toFloat(), topOffset = pad.toFloat())
            }
            return bmp
        }

        val a = render(plain)
        val b = render(withFx)
        val pa = a.toPixelMap()
        val pb = b.toPixelMap()
        var bgPixels = 0
        var linePixels = 0
        val line = plain.lines[0]
        val underlineY = 10 + line.top + plain.lineHeightPx * 0.78f
        val centerY = 10 + line.top + plain.lineHeightPx / 2f
        for (y in 0 until a.height) {
            for (x in 0 until a.width) {
                if (pa[x, y] != pb[x, y]) {
                    // 基文中线以上的浅色差异 → 底色；靠下的细线 → 下划线
                    if (y < centerY - 2) bgPixels++ else if (y > underlineY - 3 && y < underlineY + 3) linePixels++
                }
            }
        }
        // 底色：画在字身范围内（覆盖中线以上区域）
        assertTrue(bgPixels > 20, "标记底色应有明显像素差异，实测 $bgPixels")
        // 下划线：在预期 y 附近应有差异
        assertTrue(linePixels > 3, "专名号下划线应出现在 y≈${underlineY.toInt()} 附近，实测 $linePixels px")
        println(">>> D13：底色差异 $bgPixels px；下划线差异 $linePixels px（y≈${underlineY.toInt()}）")
    }
}
