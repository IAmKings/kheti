package com.kheti.layout

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.kheti.KhetiMetrics
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import java.io.File
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Phase 4 证据：行间注（ruby / `heti--annotation`）。
 *
 * 覆盖：注音居中、区间经文本归一化后的下标映射、两种模式的行高语义、真实渲染。
 */
class Phase4RubyTest {

    private fun measurer(): TextMeasurer =
        TextMeasurer(createFontFamilyResolver(), Density(1f), LayoutDirection.Ltr)

    private fun engine() = KhetiEngine(measurer(), Density(1f))

    private fun loadFont(name: String): FontFamily? {
        val f = File("../reference/fonts/$name")
        return if (f.exists()) FontFamily(androidx.compose.ui.text.platform.Font(f)) else null
    }

    // --------------------------------------------------------------- 几何

    @Test
    fun 高密度设备上注音字号不被重复放大() {
        // 回归：曾经把像素值当 sp 传给 TextUnit（`rubyFontSize.sp`），
        // 于是 density=4 的手机上注音被再乘一次 density（32px → 128px），直接盖住整行汉字。
        // 桌面测试长期用 Density(1f)，对这类密度 bug 是完全盲的——此测试专门补上。
        val density = Density(2f)
        val measurer = TextMeasurer(createFontFamilyResolver(), density, LayoutDirection.Ltr)
        val e = KhetiEngine(measurer, density)
        val style = KhetiTextStyle.of(KhetiMetrics.Size.Normal) // 16sp → 32px @density2
        val layout = e.layout(
            text = "赫蹏是中文排版增强",
            style = style,
            maxWidthPx = 672,
            ruby = listOf(KhetiRuby(0, 1, "hè")),
        )
        val rg = layout.lines[0].ruby.single()
        // 注音像素字号 = 基文的一半（16sp → 32px → 注音 16px）
        assertEquals(16f, rg.fontSizePx, 0.01f, "注音应为基文像素字号的一半")
        // 关键断言：注音"hè"在 16px 下的宽度应是十几像素，而不是被放大后的数十像素
        assertTrue(
            rg.width < rg.fontSizePx * 2.5f,
            "注音宽度异常（疑似被 density 重复放大）：width=${rg.width} fontSize=${rg.fontSizePx}",
        )
    }

    @Test
    fun 注音字号为基文一半且在基文区间上居中() {
        val style = KhetiTextStyle.of(KhetiMetrics.Size.Normal)
        val layout = engine().layout(
            text = "赫蹏是中文排版增强",
            style = style,
            maxWidthPx = 672,
            ruby = listOf(KhetiRuby(0, 1, "hè")),
        )
        val line = layout.lines[0]
        assertEquals(1, line.ruby.size)
        val rg = line.ruby[0]
        assertEquals(0, rg.baseStart)
        assertEquals(1, rg.baseEnd)
        assertEquals(8f, rg.fontSizePx, 0.01f, "注音字号应为基文 16px 的一半")

        // 注音中心应与基文「赫」的中心对齐
        val baseCenter = line.x + line.charX[0] + line.charAdvance[0] / 2f
        val annCenter = rg.x + rg.width / 2f
        assertEquals(baseCenter, annCenter, 0.6f, "注音应在基文上居中")
    }

    @Test
    fun 注音区间经文本归一化后下标正确映射() {
        // 原文含中西文之间的手敲空格：归一化会吞掉它们，ruby 区间必须跟着平移
        val text = "中文 abc 赫蹏"
        val heIndex = text.indexOf('赫')
        val plannedText = com.kheti.AdjustmentPlanner.plan(text).text
        assertEquals("中文abc赫蹏", plannedText, "前置空格应被吞掉")

        val layout = engine().layout(
            text = text,
            style = KhetiTextStyle.of(KhetiMetrics.Size.Normal),
            maxWidthPx = 672,
            ruby = listOf(KhetiRuby(heIndex, heIndex + 1, "hè")),
        )
        val rg = layout.lines[0].ruby.single()
        assertEquals(
            plannedText.indexOf('赫'),
            rg.baseStart,
            "注音区间应按归一化下标定位（原文 $heIndex → 归一化 ${plannedText.indexOf('赫')}）",
        )
        assertEquals('赫', layout.text[rg.baseStart], "注音必须落在「赫」上")
    }

    @Test
    fun 内联模式保持行高网格且注音不溢出到块外() {
        val style = KhetiTextStyle.of(KhetiMetrics.Size.Normal) // 16px / 24px
        val text = "赫蹏是中文排版增强"
        val layout = engine().layout(
            text = text,
            style = style,
            maxWidthPx = 672,
            ruby = listOf(KhetiRuby(0, 1, "hè")),
        )
        // 行盒（网格）不变：仍是 1.5 × 16 = 24px
        assertEquals(24f, layout.lineHeightPx, 0.01f, "行间注不得改变行高网格")
        // 块高 = 行盒 + 顶部为注音预留的空间（注音在行盒之上，落进行间空白）
        val reserve = layout.size.height - 24
        assertTrue(reserve > 0, "内联注音应在块顶预留空间，实测预留 ${reserve}px")

        for (rg in layout.lines[0].ruby) {
            assertTrue(rg.y >= 0f, "注音不应溢出块顶：y=${rg.y}")
            assertTrue(
                rg.y + rg.fontSizePx <= layout.size.height,
                "注音不应溢出块底：bottom=${rg.y + rg.fontSizePx} height=${layout.size.height}",
            )
            // 注音必须位于基文字身框**之上**（不能压在汉字上）
            val baseEmTop = layout.lines[0].top + layout.lineHeightPx - 16f
            assertTrue(
                rg.y + rg.fontSizePx <= baseEmTop + 8f,
                "注音底边不应深入基文：注音底=${rg.y + rg.fontSizePx} 基文顶=$baseEmTop",
            )
        }
    }

    @Test
    fun 块模式注音完整位于基文上方且行高为二点二五倍() {
        val base = KhetiTextStyle.of(KhetiMetrics.Size.Normal)
        val blockStyle = base.copy(lineHeight = base.fontSize * KhetiMetrics.LINE_HEIGHT_ANNOTATION)
        val layout = engine().layout(
            text = "庖丁为文惠君解牛",
            style = blockStyle,
            maxWidthPx = 672,
            ruby = listOf(KhetiRuby(0, 1, "páo"), KhetiRuby(1, 2, "dīng")),
            rubyMode = KhetiRubyMode.Block,
        )
        assertEquals(36f, layout.lineHeightPx, 0.01f, "块模式行高应为 2.25 × 16 = 36")
        val reserve = layout.size.height - 36
        assertTrue(reserve > 0, "块模式同样需要在块顶为注音预留空间，实测 $reserve")
        val line = layout.lines[0]
        // 基文墨迹相对行盒顶的真实位置（不能用"居中"假设）
        val m = measurer()
        val probe = m.measure(
            androidx.compose.ui.text.AnnotatedString("字"),
            KhetiTextStyle.of(KhetiMetrics.Size.Normal).toTextStyle()
                .copy(lineHeight = blockStyle.lineHeight),
        )
        val inkTop = line.top + probe.getBoundingBox(0).top
        for (rg in line.ruby) {
            assertTrue(
                rg.y + rg.fontSizePx <= inkTop + 0.01f,
                "块模式注音底边应不越过基文墨迹顶部：bottom=${rg.y + rg.fontSizePx} inkTop=$inkTop",
            )
        }
    }

    // ------------------------------------------------------------- 渲染证据

    @Test
    fun 渲染行间注为PNG并自检注音位于基文上方且居中() {
        val song = loadFont("lxgw_neozhisong_screen.ttf")
        val family = song ?: FontFamily.Default
        val m = measurer()
        val e = KhetiEngine(m, Density(1f))
        val style = KhetiTextStyle.of(KhetiMetrics.Size.Normal, family)

        // 取自赫蹏官方演示：<ruby class="heti-ruby--inline">赫<rt>hè</rt></ruby>蹏<rt>tí</rt>
        val text = "赫蹏是专为中文网页内容设计的排版样式增强，可为读者带来更好的阅读体验。"
        val layout = e.layout(
            text = text,
            style = style,
            maxWidthPx = 624,
            ruby = listOf(KhetiRuby(0, 1, "hè"), KhetiRuby(1, 2, "tí")),
        )

        val pad = 24
        val w = layout.size.width + pad * 2
        val h = layout.size.height + pad * 2
        val bitmap = ImageBitmap(w, h)
        CanvasDrawScope().draw(
            Density(1f), LayoutDirection.Ltr, Canvas(bitmap), Size(w.toFloat(), h.toFloat()),
        ) {
            drawRect(color = Color.White)
            drawKhetiText(
                measurer = m, layout = layout, style = style,
                color = Color.Black, density = Density(1f),
                leftOffset = pad.toFloat(), topOffset = pad.toFloat(),
            )
        }
        val out = File("../reference/ruby-inline.png")
        out.writeBytes(
            (Image.makeFromBitmap(bitmap.asSkiaBitmap()).encodeToData(EncodedImageFormat.PNG)
                ?: error("PNG 编码失败")).bytes
        )
        println(">>> 行间注渲染证据：${out.absolutePath}（${out.length()} 字节，$w×$h，${layout.lineCount} 行）")

        // 像素自检：与"同一版式但不带注音"的渲染做差分 —— 差异像素即注音墨迹。
        // （内联注音与基文字形在竖直方向**有意重叠**，故用条带切分不可靠，差分才准确。）
        // 注意：带注音时引擎会在块顶预留空间，基文整体下移，故基准渲染要补偿同样的偏移。
        val noRuby = e.layout(text = text, style = style, maxWidthPx = 624)
        val plainOffset = pad.toFloat() + (layout.size.height - noRuby.size.height)
        val plain = ImageBitmap(w, h)
        CanvasDrawScope().draw(
            Density(1f), LayoutDirection.Ltr, Canvas(plain), Size(w.toFloat(), h.toFloat()),
        ) {
            drawRect(color = Color.White)
            drawKhetiText(
                measurer = m, layout = noRuby, style = style,
                color = Color.Black, density = Density(1f),
                leftOffset = pad.toFloat(), topOffset = plainOffset,
            )
        }

        val px = bitmap.toPixelMap()
        val plainPx = plain.toPixelMap()
        val diff = ArrayList<Pair<Int, Int>>()
        for (y in 0 until h) {
            for (x in 0 until w) {
                if (px[x, y] != plainPx[x, y]) diff += x to y
            }
        }
        assertTrue(diff.isNotEmpty(), "带注音与不带注音的渲染必须有差异（注音已绘制）")

        val line = layout.lines[0]
        val rg = line.ruby[0]
        val annCenter = (diff.minOf { it.first } + diff.maxOf { it.first }) / 2f
        val annTop = diff.minOf { it.second }
        val annBottom = diff.maxOf { it.second }
        val baseCenter = pad + line.x + line.charX[0] + line.charAdvance[0] / 2f
        assertTrue(
            abs(annCenter - baseCenter) < 14f,
            "注音应居中于基文：注音中心=$annCenter 基文首字中心=$baseCenter",
        )
        // 基文墨迹的竖直中心
        var baseInkTop = Int.MAX_VALUE
        var baseInkBottom = -1
        for (y in 0 until h) {
            if ((0 until w).any { x -> plainPx[x, y].alpha > 0.05f && plainPx[x, y].red < 0.9f }) {
                if (y < baseInkTop) baseInkTop = y
                if (y > baseInkBottom) baseInkBottom = y
            }
        }
        val baseInkCenter = (baseInkTop + baseInkBottom) / 2f
        assertTrue(
            (annTop + annBottom) / 2f < baseInkCenter,
            "注音竖直中心应高于基文墨迹中心：注音=${(annTop + annBottom) / 2f} 基文=$baseInkCenter",
        )
        println(">>> 注音差分墨迹 ${diff.size} px：x 中心=$annCenter（基文首字中心=$baseCenter），y=$annTop-$annBottom")
        println(">>> 注音字号=${rg.fontSizePx}px；基文墨迹 y=$baseInkTop-$baseInkBottom（中心 $baseInkCenter）")
    }
}
