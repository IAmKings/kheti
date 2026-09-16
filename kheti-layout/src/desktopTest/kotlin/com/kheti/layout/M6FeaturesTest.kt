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
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * M6 功能验证：
 * 1. **竖排行间注** —— 注音应排在基文**右侧**、居中于基文区间；
 * 2. **着重号** —— 应在基文下方画出圆点。
 *
 * 两者此前都是"实现了但没有验证"，本测试补上。
 */
class M6FeaturesTest {

    private fun measurer(density: Density = Density(1f)): TextMeasurer =
        TextMeasurer(createFontFamilyResolver(), density, LayoutDirection.Ltr)

    private fun family(): FontFamily {
        val f = File("../reference/fonts/lxgw_neozhisong_screen.ttf")
        return if (f.exists()) FontFamily(androidx.compose.ui.text.platform.Font(f)) else FontFamily.Serif
    }

    private fun ink(px: androidx.compose.ui.graphics.PixelMap, x: Int, y: Int): Boolean =
        px[x, y].alpha > 0.05f && px[x, y].red < 0.9f

    // ------------------------------------------------------- 1. 竖排行间注

    @Test
    fun 竖排注音排在基文右侧且居中于基文区间() {
        val style = KhetiTextStyle.of(KhetiMetrics.Size.XLarge, family())
        val e = KhetiVerticalEngine(measurer(), Density(1f))
        val layout = e.layout(
            text = "赫蹏",
            style = style,
            maxHeightPx = 400,
            ruby = listOf(KhetiRuby(0, 1, "hè")),
        )
        val col = layout.columns[0]
        assertEquals(2, col.ruby.size, "注音应逐字拆成 2 个字形（h、è）")
        assertEquals("hè", col.ruby.map { it.ch }.joinToString(""))

        // 基文「赫」：字号 20px，字身框水平居中于 30px 列宽 → 右缘在 25px 处
        val emRight = (layout.columnWidthPx - layout.fontSizePx) / 2f + layout.fontSizePx
        for (g in col.ruby) {
            assertTrue(g.x >= emRight, "注音 x=${g.x} 应在基文字身框右缘 $emRight 之右")
        }
        // 注音整体应居中于基文区间
        val run = col.runs.first()
        val baseCenter = run.y + run.advance / 2f
        val rubyTop = col.ruby.first().y
        val rubyBottom = col.ruby.last().y + col.ruby.last().fontSizePx
        assertEquals(baseCenter, (rubyTop + rubyBottom) / 2f, 0.6f, "注音应居中于基文")

        // 最右列右侧必须预留注音宽度，否则会被画布裁掉
        val rightmost = layout.columns.maxByOrNull { it.x }!!
        val need = rightmost.x + rightmost.ruby.maxOf { it.x + it.fontSizePx }
        assertTrue(
            layout.size.width >= need - 0.5f,
            "画布宽 ${layout.size.width} 未覆盖注音右缘 $need",
        )
    }

    @Test
    fun 竖排注音渲染在基文右侧() {
        val style = KhetiTextStyle.of(KhetiMetrics.Size.XLarge, family())
        val m = measurer()
        val e = KhetiVerticalEngine(m, Density(1f))
        val layout = e.layout(
            text = "赫蹏",
            style = style,
            maxHeightPx = 400,
            ruby = listOf(KhetiRuby(0, 1, "hè")),
        )
        val pad = 10
        val w = layout.size.width + pad * 2
        val h = layout.size.height + pad * 2
        val bmp = ImageBitmap(w, h)
        CanvasDrawScope().draw(
            Density(1f), LayoutDirection.Ltr, Canvas(bmp), Size(w.toFloat(), h.toFloat()),
        ) {
            drawRect(color = Color.White)
            drawKhetiVerticalText(
                measurer = m, layout = layout, style = style,
                color = Color.Black, density = Density(1f),
                left = pad.toFloat(), top = pad.toFloat(),
            )
        }
        val px = bmp.toPixelMap()
        val col = layout.columns[0]
        // 基文字身框右缘在画布中的 x
        val emRight = pad + col.x + (layout.columnWidthPx - layout.fontSizePx) / 2f + layout.fontSizePx
        // 取「赫」所在竖轴范围，检查其右侧是否存在墨迹（注音）
        val run = col.runs.first()
        val y0 = (pad + col.top + run.y).toInt()
        val y1 = (pad + col.top + run.y + run.advance).toInt()
        var inkRightOfEm = 0
        for (y in y0..y1) {
            for (x in (emRight + 1).toInt() until w) if (ink(px, x, y)) inkRightOfEm++
        }
        assertTrue(inkRightOfEm > 5, "基文右侧应有注音墨迹，实测 $inkRightOfEm px")
        println(">>> 竖排行间注：基文右侧注音墨迹 $inkRightOfEm px（字身框右缘 x=${emRight.toInt()}）")
    }

    // ----------------------------------------------------------- 2. 着重号

    @Test
    fun 着重号在基文下方绘制圆点() {
        val style = KhetiTextStyle.of(KhetiMetrics.Size.Normal, family())
        val m = measurer()
        val e = KhetiEngine(m, Density(1f))
        val text = "我们必将战胜"
        val plain = e.layout(text, style, maxWidthPx = 672)
        val marked = e.layout(text, style, maxWidthPx = 672)

        fun render(withMarks: Boolean): ImageBitmap {
            val pad = 10
            val w = plain.size.width + pad * 2
            val h = plain.size.height + pad * 2 + 12
            val bmp = ImageBitmap(w, h)
            CanvasDrawScope().draw(
                Density(1f), LayoutDirection.Ltr, Canvas(bmp), Size(w.toFloat(), h.toFloat()),
            ) {
                drawRect(color = Color.White)
                drawKhetiText(
                    measurer = m, layout = plain, style = style,
                    color = Color.Black, density = Density(1f),
                    leftOffset = pad.toFloat(), topOffset = pad.toFloat(),
                    emphasis = if (withMarks) listOf(2..3) else emptyList(),
                )
            }
            return bmp
        }

        val a = render(false)
        val b = render(true)
        val pa = a.toPixelMap()
        val pb = b.toPixelMap()
        val diff = ArrayList<Pair<Int, Int>>()
        for (y in 0 until a.height) {
            for (x in 0 until a.width) if (pa[x, y] != pb[x, y]) diff += x to y
        }
        assertTrue(diff.isNotEmpty(), "开启着重号后应有差异像素（圆点已绘制）")

        // 圆点应位于基文**下方**：其竖直中心要低于基文墨迹的中线
        val line = plain.lines[0]
        val baseMid = 10 + line.top + plain.lineHeightPx / 2f
        val markCenter = diff.map { it.second }.average().toFloat()
        assertTrue(
            markCenter > baseMid,
            "着重号应画在基文下方：圆点中心 y=$markCenter 应低于基文中线 $baseMid",
        )
        // 只应出现在被标记的两个字下方（x 范围受限于第 3、4 字）
        val x0 = 10 + line.x + line.charX[2]
        val x1 = 10 + line.x + line.charX[3] + line.charAdvance[3]
        assertTrue(
            diff.all { it.first >= x0 - 2 && it.first <= x1 + 2 },
            "着重号只应出现在被标记的字下方",
        )
        println(">>> 着重号：差异像素 ${diff.size} 个，中心 y=${markCenter.toInt()}（基文中线 ${baseMid.toInt()}），x∈[$x0, $x1]")
    }
}
