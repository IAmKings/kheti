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
import kotlin.test.assertTrue

/**
 * D13 收尾：`sup` / `sub` / `small` —— 会**改变 advance** 的三类行内样式。
 *
 * 关键点：必须按缩放后的样式单独度量该字，否则后续文字位置会沿用原字号宽度而错位。
 */
class M8ScaleInlineTest {

    private fun family(): FontFamily {
        val f = File("../reference/fonts/lxgw_neozhisong_screen.ttf")
        return if (f.exists()) FontFamily(androidx.compose.ui.text.platform.Font(f)) else FontFamily.Serif
    }

    private fun engine(): KhetiEngine =
        KhetiEngine(
            TextMeasurer(createFontFamilyResolver(), Density(1f), LayoutDirection.Ltr),
            Density(1f),
        )

    private val text = "正文X正文"

    @Test
    fun 上下标与小号按比例缩小字号并改变后续位置() {
        val style = KhetiTextStyle.of(KhetiMetrics.Size.Normal, family())
        val e = engine()
        val plain = e.layout(text, style, 672)
        val sup = e.layout(text, style, 672, inline = listOf(KhetiInlineSpan(2, 3, KhetiInlineKind.Sup)))
        val sub = e.layout(text, style, 672, inline = listOf(KhetiInlineSpan(2, 3, KhetiInlineKind.Sub)))
        val small = e.layout(text, style, 672, inline = listOf(KhetiInlineSpan(2, 3, KhetiInlineKind.Small)))

        val base = plain.lines[0].charAdvance[2]
        assertEquals(base * 0.8f, sup.lines[0].charAdvance[2], 0.6f, "sup 应为 0.8 倍宽")
        assertEquals(base * 0.8f, sub.lines[0].charAdvance[2], 0.6f, "sub 应为 0.8 倍宽")
        assertEquals(base * 0.875f, small.lines[0].charAdvance[2], 0.6f, "small 应为 0.875 倍宽")

        // 后续文字位置必须随之左移（说明确实按缩放后宽度度量了）
        assertTrue(
            sup.lines[0].charX[3] < plain.lines[0].charX[3] - 0.5f,
            "上标之后文字应左移：sup=${sup.lines[0].charX[3]} plain=${plain.lines[0].charX[3]}",
        )

        // 缩放与基线位移已解析为逐字符效果
        val fx = assertNotNull(sup.inline)
        assertEquals(0.8f, fx.scale[2], 0.001f)
        assertEquals(-0.35f, fx.shiftEm[2], 0.001f)
        assertEquals(0.2f, assertNotNull(sub.inline).shiftEm[2], 0.001f)
        assertEquals(1f, fx.scale[0], 0.001f, "未标记的字不应被缩放")
        println(
            ">>> D13：advance 原=%.2f sup=%.2f small=%.2f；后续字 x 原=%.2f sup=%.2f".format(
                base, sup.lines[0].charAdvance[2], small.lines[0].charAdvance[2],
                plain.lines[0].charX[3], sup.lines[0].charX[3],
            ),
        )
    }

    @Test
    fun 上标渲染时字形确实上移且更小() {
        val style = KhetiTextStyle.of(KhetiMetrics.Size.Normal, family())
        val m = TextMeasurer(createFontFamilyResolver(), Density(1f), LayoutDirection.Ltr)
        val e = KhetiEngine(m, Density(1f))
        val plain = e.layout(text, style, 672)
        val sup = e.layout(text, style, 672, inline = listOf(KhetiInlineSpan(2, 3, KhetiInlineKind.Sup)))

        fun render(l: KhetiLayout): ImageBitmap {
            val pad = 10
            val w = l.size.width + pad * 2
            val h = l.size.height + pad * 2 + 10
            val bmp = ImageBitmap(w, h)
            CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(bmp), Size(w.toFloat(), h.toFloat())) {
                drawRect(color = Color.White)
                drawKhetiText(m, l, style, Color.Black, Density(1f), leftOffset = pad.toFloat(), topOffset = pad.toFloat())
            }
            return bmp
        }

        fun inkRangeY(bmp: ImageBitmap, x0: Int, x1: Int): IntRange {
            val px = bmp.toPixelMap()
            var top = Int.MAX_VALUE
            var bottom = Int.MIN_VALUE
            for (y in 0 until bmp.height) {
                for (x in x0 until x1) {
                    if (px[x, y].alpha > 0.05f && px[x, y].red < 0.9f) {
                        if (y < top) top = y
                        if (y > bottom) bottom = y
                    }
                }
            }
            return top..bottom
        }

        val line = plain.lines[0]
        val x0 = (10 + line.x + line.charX[2]).toInt() + 2
        val x1 = (10 + line.x + line.charX[2] + line.charAdvance[2] * 0.6f).toInt()
        val plainInk = inkRangeY(render(plain), x0, x1)
        val supInk = inkRangeY(render(sup), x0, x1)
        assertTrue(
            supInk.first < plainInk.first,
            "上标墨迹应比正文更高：sup=$supInk plain=$plainInk",
        )
        assertTrue(
            (supInk.last - supInk.first) < (plainInk.last - plainInk.first),
            "上标字形应更小：sup=$supInk plain=$plainInk",
        )
        println(">>> D13：上标墨迹 y=$supInk（高 ${supInk.last - supInk.first}），正文 y=$plainInk（高 ${plainInk.last - plainInk.first}）")
    }
}
