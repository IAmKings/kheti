package com.kheti.layout

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Constraints
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
 * Phase 2 证据：诗词版式几何 + 真实渲染。
 *
 * 断言只依赖**相对几何**（增量、行盒、对齐），不依赖具体字体度量，
 * 这样换字体/换平台仍然成立。
 */
class Phase2PoetryTest {

    private val style = KhetiTextStyle.of(KhetiMetrics.Size.XLarge)

    private fun engine(family: FontFamily = FontFamily.Default): KhetiEngine {
        val measurer = TextMeasurer(familyResolver(), Density(1f), LayoutDirection.Ltr)
        return KhetiEngine(measurer, Density(1f))
    }

    private fun familyResolver() = androidx.compose.ui.text.font.createFontFamilyResolver()

    private fun loadFont(name: String): FontFamily? {
        val f = File("../reference/fonts/$name")
        if (!f.exists()) return null
        return FontFamily(androidx.compose.ui.text.platform.Font(f))
    }

    // ------------------------------------------------------- 1. 诗词版式几何

    @Test
    fun 诗词行居中且无首行缩进() {
        val e = engine()
        val line = "李白乘舟将欲行，忽闻岸上踏歌声。"
        val layout = e.layout(
            text = line,
            style = style,
            maxWidthPx = 672,
            alignment = KhetiAlignment.Center,
        )
        assertEquals(1, layout.lineCount, "672px 内 16 字行应单行放下")
        val l = layout.lines[0]
        // 居中：左右留白相等
        val rightGap = 672f - (l.x + l.width)
        assertTrue(abs(l.x - rightGap) < 0.01f, "居中失败：left=${l.x} right=$rightGap")
        assertTrue(l.x > 0f, "居中行应有左侧留白")
    }

    @Test
    fun 标点挤压在引擎中精确落地为半个字宽() {
        val e = engine()
        val text = "借指纸。《汉书"
        val layout = e.layout(text, style, maxWidthPx = 1000, alignment = KhetiAlignment.Start)
        val l = layout.lines[0]
        val k = text.indexOf('。')
        // 上游净效果 = −0.5em 挤压 + 该字符自身的字距（x-large 为 0.05em）→ −0.45em = −9px
        val expected = (-0.5f + style.letterSpacingEm) * 20f
        assertEquals(
            expected,
            l.charX[k + 1] - (l.charX[k] + l.charAdvance[k]),
            0.01f,
            "。《 应左移 ½ 字宽（叠加该字自身字距）",
        )
    }

    @Test
    fun 中西文间距在引擎中落地为四分之一字宽两侧() {
        val e = engine()
        val layout = e.layout("中文 abc 中文", style, maxWidthPx = 1000, alignment = KhetiAlignment.Start)
        val l = layout.lines[0]
        val s = layout.text
        assertEquals("中文abc中文", s, "手敲空格应被吞掉")
        val a = s.indexOf('a')
        val c = s.indexOf('c')
        // 'a' 的位移 = 前一个 CJK 字的字距(0.05em) + gap 的 ¼ 字宽边距(0.25em) = 0.30em = 6px
        val shiftToA = l.charX[a] - (l.charX[a - 1] + l.charAdvance[a - 1])
        assertEquals(0.30f * 20f, shiftToA, 0.01f, "CJK→西文 应留 ¼ 字宽（含前字字距）")
        // 'c' 之后：gap 区间字距为 normal，只剩 ¼ 字宽边距 = 5px
        val shiftAfterC = l.charX[c + 1] - (l.charX[c] + l.charAdvance[c])
        assertEquals(0.25f * 20f, shiftAfterC, 0.01f, "西文→CJK 应留 ¼ 字宽（gap 内字距为 normal）")
    }

    @Test
    fun 行末标点悬挂不计入行宽且溢出到行外() {
        val e = engine()
        val poem = "独在异乡为异客，"
        val plain = e.layout(poem, style, 672, KhetiAlignment.Center, hang = KhetiHang.Off)
        val hung = e.layout(poem, style, 672, KhetiAlignment.Center, hang = KhetiHang.LineEnd)

        val comma = poem.length - 1
        assertEquals(comma, hung.lines[0].hangingIndex, "行末逗号应被判定为可悬挂")
        val w = plain.lines[0].width - hung.lines[0].width
        assertEquals(20f, w, 0.6f, "悬挂后行宽应减少约一个字宽（20px）")
        // 悬挂标点仍被绘制（在其自然位置），因此墨迹范围会超出内容宽度
        val hangBox = hung.getBoundingBox(comma)
        assertTrue(
            hangBox.right > hung.lines[0].x + hung.lines[0].width,
            "悬挂标点应溢出内容宽度：box.right=${hangBox.right} content=${hung.lines[0].x + hung.lines[0].width}",
        )
    }

    @Test
    fun 两端对齐按字间分配余量() {
        val e = engine()
        val body = KhetiTextStyle.of(KhetiMetrics.Size.Normal)
        val text = "先帝创业未半而中道崩殂，今天下三分，益州疲弊，此诚危急存亡之秋也。"
        val layout = e.layout(text, body, maxWidthPx = 336, alignment = KhetiAlignment.Justify)
        assertTrue(layout.lineCount >= 2, "应折成多行才有非末行可对齐")
        val first = layout.lines[0]
        assertEquals(336f, first.width, 0.5f, "非末行两端对齐后应填满行宽")
        assertTrue(first.charX[1] - first.charX[0] > first.charAdvance[0], "字间应被拉开")
        // 末行不拉伸（与浏览器/CSS 一致）
        val last = layout.lines.last()
        assertTrue(last.width < 336f, "末行不应被拉伸")
    }

    @Test
    fun 超长文本按平台断行建议折行且行盒贴合网格() {
        val e = engine()
        val text = "先帝创业未半而中道崩殂，今天下三分，益州疲弊，此诚危急存亡之秋也。"
        val layout = e.layout(text, KhetiTextStyle.of(KhetiMetrics.Size.Normal), maxWidthPx = 336)
        assertTrue(layout.lineCount >= 2, "应折成多行")
        for (i in layout.lines.indices) {
            assertEquals(i * 24f, layout.getLineTop(i), 0.01f, "行盒必须贴合 24px 网格")
        }
        // 行首不应出现禁则标点
        for (l in layout.lines.drop(1)) {
            assertTrue(
                !KhetiMetrics.isNoLineStart(layout.text[l.start]),
                "行首出现禁则标点：${layout.text[l.start]}",
            )
        }
    }

    // --------------------------------------------------------- 2. 真实渲染

    @Test
    fun 渲染赠汪伦为PNG证据() {
        val kai = loadFont("lxgw_wenkai_regular.ttf")
        val song = loadFont("lxgw_neozhisong_screen.ttf")
        val family = kai ?: FontFamily.Default
        val measurer = TextMeasurer(familyResolver(), Density(1f), LayoutDirection.Ltr)
        val e = KhetiEngine(measurer, Density(1f))

        val width = 672
        val titleStyle = KhetiTextStyle.of(KhetiMetrics.Size.XLarge, family)
        val metaStyle = KhetiTextStyle.of(
            KhetiMetrics.Size.Small,
            song ?: family,
        )
        val verseStyle = KhetiTextStyle.of(KhetiMetrics.Size.XLarge, song ?: family)

        val title = e.layout("赠汪伦", titleStyle, width, KhetiAlignment.Center)
        val meta = e.layout("〔唐〕李白", metaStyle, width, KhetiAlignment.Center)
        val verses = listOf(
            "李白乘舟将欲行，",
            "忽闻岸上踏歌声。",
            "桃花潭水深千尺，",
            "不及汪伦送我情。",
        ).map { e.layout(it, verseStyle, width, KhetiAlignment.Center, hang = KhetiHang.LineEnd) }

        val padding = 32f
        val gap = 24f
        val height = padding * 2 + title.size.height + meta.size.height + gap +
            verses.sumOf { it.size.height }

        val bitmap = ImageBitmap(width + (padding * 2).toInt(), height.toInt())
        val canvas = Canvas(bitmap)
        CanvasDrawScope().draw(
            Density(1f),
            LayoutDirection.Ltr,
            canvas,
            Size(bitmap.width.toFloat(), bitmap.height.toFloat()),
        ) {
            drawRect(color = androidx.compose.ui.graphics.Color.White)
            var y = padding
            drawKhetiText(measurer, title, titleStyle, androidx.compose.ui.graphics.Color.Black, Density(1f), topOffset = y)
            y += title.size.height
            drawKhetiText(measurer, meta, metaStyle, androidx.compose.ui.graphics.Color(0xFF556677), Density(1f), topOffset = y)
            y += meta.size.height + gap
            for (v in verses) {
                drawKhetiText(measurer, v, verseStyle, androidx.compose.ui.graphics.Color.Black, Density(1f), topOffset = y)
                y += v.size.height
            }
        }

        val out = File("../reference/poem-horizontal.png")
        out.parentFile?.mkdirs()
        val data = Image.makeFromBitmap(bitmap.asSkiaBitmap())
            .encodeToData(EncodedImageFormat.PNG)
            ?: error("PNG 编码失败")
        out.writeBytes(data.bytes)
        assertTrue(out.length() > 1000, "PNG 输出异常：${out.length()} 字节")
        println(">>> 已输出渲染证据：${out.absolutePath}（${out.length()} 字节）")
        println(">>> 字体：楷体=${kai != null} 宋体=${song != null}")
        println(">>> 版式：标题 ${title.size.height}px + 元信息 ${meta.size.height}px + 诗句 ${verses.size} 行 × ${verses[0].size.height}px")

        // ---- 像素级结构自检（不依赖人眼）----
        // 四行诗句应各自居中，且行末悬挂标点应超出"内容右边界"
        val px = bitmap.toPixelMap()
        fun inkOfBand(top: Int, height: Int): Pair<Int, Int>? {
            var min = Int.MAX_VALUE
            var max = Int.MIN_VALUE
            for (y in top until (top + height).coerceAtMost(bitmap.height)) {
                for (x in 0 until bitmap.width) {
                    if (px[x, y].alpha > 0.05f && px[x, y].red < 0.9f) {
                        if (x < min) min = x
                        if (x > max) max = x
                    }
                }
            }
            return if (max < min) null else min to max
        }

        var bandTop = (padding + title.size.height + meta.size.height + gap).toInt()
        var firstLineX = Float.NaN
        for ((i, v) in verses.withIndex()) {
            val range = inkOfBand(bandTop, v.size.height) ?: error("第 $i 行没有墨迹")
            val line = v.lines[0]

            // 1) 墨迹左边界应贴近行起点（差值为字形左侧留白）
            assertTrue(
                range.first - line.x in -1f..12f,
                "第 $i 行左边界异常：ink=${range.first} lineX=${line.x}",
            )
            // 2) 悬挂标点必须溢出内容宽度（上游 heti-hang 为绝对定位、不占行宽）
            val overflow = range.second - (line.x + line.width)
            assertTrue(
                overflow > 2f,
                "第 $i 行未见悬挂溢出：ink=${range.second} 内容右边界=${line.x + line.width}",
            )
            // 3) 悬挂量应小于一个全角标点宽度（20px），否则说明计宽有误
            assertTrue(overflow < line.charAdvance.last(), "第 $i 行悬挂量异常：$overflow")

            // 4) 各行字数相同 → 字符网格必须完全对齐（这是诗词悬挂的排版意图：
            //    字对齐成列，标点挂在列外）
            if (firstLineX.isNaN()) firstLineX = line.x
            assertEquals(firstLineX, line.x, 0.01f, "第 $i 行与首行未对齐（悬挂应挂在字列之外）")

            println(
                ">>> 第 $i 行 ink=[${range.first}, ${range.second}] 行起点=${line.x.toInt()} " +
                    "内容宽=${line.width.toInt()} 悬挂溢出=${overflow.toInt()}px",
            )
            bandTop += v.size.height.toInt()
        }
    }
}
