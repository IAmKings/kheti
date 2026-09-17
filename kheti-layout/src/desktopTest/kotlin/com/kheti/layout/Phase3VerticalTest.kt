package com.kheti.layout

import androidx.compose.ui.geometry.Offset
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
import com.kheti.KhetiRegion
import com.kheti.VerticalGlyph
import com.kheti.VerticalOrientation
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import java.io.File
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Phase 3 证据：竖排（`writing-mode: vertical-rl` 的等价物）。
 *
 * 断言只依赖**相对几何与朝向**，不依赖具体字体度量。
 */
class Phase3VerticalTest {

    private val poemLines = listOf(
        "李白乘舟将欲行，",
        "忽闻岸上踏歌声。",
        "桃花潭水深千尺，",
        "不及汪伦送我情。",
    )

    // 字体钉住已提交的参考字体，而不是平台默认字体：
    // 默认字体的 CJK advance 因宿主而异（本机 = 字号，CI runner 不同），
    // 会让"字距 0.125em → 步进 22.5px"这类**绝对几何**断言只在生成它的机器上成立。
    // 参考字体随仓库提交且 CJK 等宽，本机/CI/真机结果一致。
    private val style = KhetiTextStyle.of(
        KhetiMetrics.Size.XLarge,
        loadFont("lxgw_neozhisong_screen.ttf") ?: FontFamily.Default,
    )

    private fun engine(family: FontFamily = FontFamily.Default): KhetiVerticalEngine {
        val measurer = TextMeasurer(createFontFamilyResolver(), Density(1f), LayoutDirection.Ltr)
        return KhetiVerticalEngine(measurer, Density(1f))
    }

    private fun measurer(): TextMeasurer =
        TextMeasurer(createFontFamilyResolver(), Density(1f), LayoutDirection.Ltr)

    private fun loadFont(name: String): FontFamily? {
        val f = File("../reference/fonts/$name")
        return if (f.exists()) FontFamily(androidx.compose.ui.text.platform.Font(f)) else null
    }

    // ------------------------------------------------------- 1. 朝向与标点表

    @Test
    fun 数字段朝向与縦中横适用范围() {
        // 回归：曾经把 3 位数字也做成縦中横，导致宽出字身框、压住后面的 `）`
        assertEquals(VerticalOrientation.Upright, com.kheti.digitRunOrientation(1))
        assertEquals(VerticalOrientation.CombineUpright, com.kheti.digitRunOrientation(2))
        assertEquals(VerticalOrientation.Sideways, com.kheti.digitRunOrientation(3))
        assertEquals(VerticalOrientation.Sideways, com.kheti.digitRunOrientation(4))
    }

    @Test
    fun 竖排元信息中每个字形的墨迹都居中于列() {
        // 回归：早期用整行高样式绘制旋转字形，行距空白把括号推偏，
        // 真机表现就是"【】/〔〕与朝代字没有居中对齐"。这里做真实渲染 + 逐单元像素检查。
        val family = loadFont("lxgw_neozhisong_screen.ttf") ?: FontFamily.Default
        val verseStyle = KhetiTextStyle.of(KhetiMetrics.Size.XLarge, family)
        val m = measurer()
        val layout = KhetiVerticalEngine(m, Density(1f))
            .layout("〔唐〕李白（701—762）", verseStyle, maxHeightPx = 900)
        val col = layout.columns[0]

        val pad = 8
        val w = layout.size.width + pad * 2
        val h = layout.size.height + pad * 2
        val bmp = ImageBitmap(w, h)
        CanvasDrawScope().draw(
            Density(1f), LayoutDirection.Ltr, Canvas(bmp), Size(w.toFloat(), h.toFloat()),
        ) {
            drawRect(color = Color.White)
            drawKhetiVerticalText(
                measurer = m, layout = layout, style = verseStyle,
                color = Color.Black, density = Density(1f),
                left = pad.toFloat(), top = pad.toFloat(),
            )
        }
        val px = bmp.toPixelMap()
        val colCenter = pad + col.x + layout.columnWidthPx / 2f
        var checked = 0
        for (run in col.runs) {
            val y0 = (pad + col.top + run.y).toInt().coerceIn(0, h - 1)
            val y1 = (pad + col.top + run.y + run.advance).toInt().coerceIn(0, h - 1)
            var min = Int.MAX_VALUE
            var max = Int.MIN_VALUE
            for (y in y0..y1) {
                for (x in 0 until w) {
                    if (px[x, y].alpha > 0.05f && px[x, y].red < 0.9f) {
                        if (x < min) min = x
                        if (x > max) max = x
                    }
                }
            }
            if (max < min) continue
            checked++
            val c = (min + max) / 2f
            assertTrue(
                abs(c - colCenter) < layout.fontSizePx * 0.6f,
                "字形「${layout.text.substring(run.start, run.end)}」墨迹中心 $c 偏离列中心 $colCenter",
            )
        }
        assertTrue(checked >= 8, "应检查到至少 8 个字形单元，实际 $checked")
    }

    @Test
    fun 竖排元信息不应丢失末尾括号() {
        // 真机反馈：竖排时「〔唐〕李白（701—762）」看不到末尾的 ）
        val text = "〔唐〕李白（701—762）"
        val layout = engine().layout(text, style, maxHeightPx = 900)
        val covered = BooleanArray(layout.text.length)
        for (col in layout.columns) {
            for (run in col.runs) for (i in run.start until run.end) covered[i] = true
        }
        val missing = (0 until layout.text.length).filter { !covered[it] }
        println(">>> 竖排元信息列数=${layout.columns.size} 覆盖=${covered.count { it }}/${layout.text.length} 缺=${missing.map { layout.text[it] }}")
        for (run in layout.columns[0].runs) {
            println("    run[${run.start},${run.end}) '${layout.text.substring(run.start, run.end)}' ${run.orientation} y=${run.y} adv=${run.advance}")
        }
        assertTrue(missing.isEmpty(), "以下字符未被任何列覆盖：${missing.map { layout.text[it] }}")
    }

    @Test
    fun 画布高度必须覆盖含悬挂标点在内的所有字形() {
        // 回归：列高会扣除悬挂标点的占位，但**画布高度**若也扣掉，
        // 挂出列尾的字形就落在画布之外被裁掉（真机表现：竖排末尾的 ） 看不见）
        val e = engine()
        for (text in listOf("〔唐〕李白（701—762）", "独在异乡为异客，", "忽闻岸上踏歌声。")) {
            val layout = e.layout(text, style, maxHeightPx = 900)
            val col = layout.columns[0]
            val last = col.runs.last()
            val lastEnd = last.y + last.advance
            assertTrue(
                layout.size.height >= lastEnd - 0.01f,
                "「$text」画布高 ${layout.size.height} 未覆盖末字形末端 $lastEnd",
            )
        }
    }

    @Test
    fun 字形朝向分类符合竖排规范() {
        // 汉字/假名正立
        assertEquals(VerticalOrientation.Upright, VerticalGlyph.defaultOrientation('李'))
        assertEquals(VerticalOrientation.Upright, VerticalGlyph.defaultOrientation('あ'))
        // 句读正立（但需按区域挪角位）
        assertEquals(VerticalOrientation.Upright, VerticalGlyph.defaultOrientation('。'))
        // 括号/破折号/省略号旋转
        assertEquals(VerticalOrientation.Sideways, VerticalGlyph.defaultOrientation('《'))
        assertEquals(VerticalOrientation.Sideways, VerticalGlyph.defaultOrientation('（'))
        assertEquals(VerticalOrientation.Sideways, VerticalGlyph.defaultOrientation('—'))
        assertEquals(VerticalOrientation.Sideways, VerticalGlyph.defaultOrientation('…'))
    }

    @Test
    fun 标点区域位移大陆靠角台湾居中() {
        // 大陆：横排墨迹在左下 → 竖排需平移到右上 (+½, −½) em
        assertEquals(0.5f to -0.5f, VerticalGlyph.punctuationOffsetEm('。', KhetiRegion.CN))
        // 台湾：居中 → 平移 (+¼, −¼) em
        assertEquals(0.25f to -0.25f, VerticalGlyph.punctuationOffsetEm('。', KhetiRegion.TW))
        // 本就居中的间隔符不位移
        assertEquals(0f to 0f, VerticalGlyph.punctuationOffsetEm('・', KhetiRegion.CN))
    }

    // ------------------------------------------------------------- 2. 版式几何

    @Test
    fun 竖排列序为右到左且每句一列() {
        val layout = engine().layout(poemLines.joinToString("\n"), style, maxHeightPx = 600)
        assertEquals(4, layout.columnCount, "四条诗句应为四列（\\n 强制换列）")
        val xs = layout.columns.map { it.x }
        assertEquals(xs.sortedDescending(), xs, "列序必须右→左：$xs")
        for (col in layout.columns) {
            assertEquals(
                poemLines[col.index].length,
                col.runs.size,
                "每列单元数应等于该句字数（7 字 + 1 标点 = 8）",
            )
        }
    }

    @Test
    fun 竖排字距为八分之一字宽且沿竖轴累加() {
        val layout = engine().layout("中文竖排", style, maxHeightPx = 600)
        val col = layout.columns[0]
        // 字号 20px：每字 advance = 20，字距 0.125em = 2.5px → 累计步进 22.5px
        val ys = col.runs.map { it.y }
        for (i in 1 until ys.size) {
            assertEquals(22.5f, ys[i] - ys[i - 1], 0.01f, "第 $i 字沿竖轴步进应为 字号 + 0.125em")
        }
    }

    @Test
    fun 竖排也能做标点挤压且方向沿竖轴() {
        val layout = engine().layout("借指纸。《汉书", style, maxHeightPx = 800)
        val col = layout.columns[0]
        val k = layout.text.indexOf('。')
        val squeezeRun = col.runs.first { it.start == k + 1 }
        val punctRun = col.runs.first { it.start == k }
        val natural = punctRun.y + punctRun.advance
        // 《 相对自然位置应上移 0.5em − 字距 = 0.5×20 − 0.125×20 = 7.5px
        assertEquals(-7.5f, squeezeRun.y - natural, 0.01f, "。《 在竖排中应上移（等价于横排的左移）")
    }

    @Test
    fun 竖排悬挂标点不计入列高() {
        val e = engine()
        val withHang = e.layout("独在异乡为异客，", style, maxHeightPx = 800, hang = KhetiHang.LineEnd)
        val noHang = e.layout("独在异乡为异客，", style, maxHeightPx = 800, hang = KhetiHang.Off)
        val comma = "独在异乡为异客，".length - 1
        assertEquals(comma, withHang.columns[0].hangingIndex, "列末标点应被判定为可悬挂")
        assertEquals(
            22.5f,
            noHang.columns[0].height - withHang.columns[0].height,
            0.6f,
            "悬挂后列高应减少约一个字的占位",
        )
    }

    // ------------------------------------------------------------- 3. 渲染证据

    @Test
    fun 渲染竖排赠汪伦为PNG并自检列序() {
        val kai = loadFont("lxgw_wenkai_regular.ttf")
        val song = loadFont("lxgw_neozhisong_screen.ttf") ?: kai
        val family = song ?: FontFamily.Default
        val m = measurer()
        val e = KhetiVerticalEngine(m, Density(1f))
        val verseStyle = KhetiTextStyle.of(KhetiMetrics.Size.XLarge, family)

        val layout = e.layout(poemLines.joinToString("\n"), verseStyle, maxHeightPx = 460, region = KhetiRegion.CN)

        val pad = 28
        val titleH = 76
        val bmpW = layout.size.width + pad * 2
        val bmpH = titleH + layout.size.height + pad * 2
        val bitmap = ImageBitmap(bmpW, bmpH)
        val canvas = Canvas(bitmap)
        CanvasDrawScope().draw(
            Density(1f), LayoutDirection.Ltr, canvas, Size(bmpW.toFloat(), bmpH.toFloat()),
        ) {
            drawRect(color = Color.White)
            // 标题横排居中（现代诗词页常见做法；赫蹏竖排演示里标题也在竖排流中）
            val titleStyle = KhetiTextStyle.heading(KhetiMetrics.Heading.H2, kai ?: family)
            val titleLayout = KhetiEngine(m, Density(1f)).layout(
                text = "赠汪伦",
                style = titleStyle,
                maxWidthPx = layout.size.width,
                alignment = KhetiAlignment.Center,
            )
            drawKhetiText(
                measurer = m, layout = titleLayout, style = titleStyle,
                color = Color(0xFF1B365D), density = Density(1f),
                leftOffset = pad.toFloat(), topOffset = pad.toFloat() - 12f,
            )
            drawKhetiVerticalText(
                measurer = m, layout = layout, style = verseStyle,
                color = Color.Black, density = Density(1f), region = KhetiRegion.CN,
                left = pad.toFloat(), top = (titleH + pad).toFloat(),
            )
        }

        val out = File("../reference/poem-vertical.png")
        out.writeBytes(
            (Image.makeFromBitmap(bitmap.asSkiaBitmap()).encodeToData(EncodedImageFormat.PNG)
                ?: error("PNG 编码失败")).bytes
        )
        println(">>> 竖排渲染证据：${out.absolutePath}（${out.length()} 字节，${bmpW}×${bmpH}）")

        // 像素自检：四列的墨迹横坐标应严格右→左，且各列纵向跨度接近
        val px = bitmap.toPixelMap()
        fun columnInkRange(colIndex: Int): IntRange {
            val col = layout.columns[colIndex]
            val x0 = (pad + col.x).toInt()
            val x1 = (pad + col.x + layout.columnWidthPx).toInt().coerceAtMost(bmpW - 1)
            var min = Int.MAX_VALUE
            var max = Int.MIN_VALUE
            for (x in x0..x1) {
                for (y in 0 until bmpH) {
                    if (px[x, y].alpha > 0.05f && px[x, y].red < 0.9f) {
                        if (x < min) min = x
                        if (x > max) max = x
                    }
                }
            }
            return min..max
        }

        val centers = (0 until layout.columnCount).map { columnInkRange(it).let { r -> (r.first + r.last) / 2f } }
        assertEquals(
            centers.sortedDescending(),
            centers,
            "四列墨迹中心必须右→左：$centers",
        )
        println(">>> 四列墨迹中心 x=$centers（列宽 ${layout.columnWidthPx.toInt()}px，右→左）")
        for (i in 0 until layout.columnCount) {
            val r = columnInkRange(i)
            assertTrue(r.last - r.first > 4, "第 $i 列墨迹过窄：$r")
        }

        // 区域规范差异：大陆 vs 台湾应产生不同像素（证明角位表真的生效）
        val tw = renderVerticalOnly(m, layout, verseStyle, KhetiRegion.TW)
        var diff = 0
        val twPx = tw.toPixelMap()
        for (y in 0 until minOf(tw.height, bitmap.height)) {
            for (x in 0 until minOf(tw.width, bitmap.width)) {
                if (twPx[x, y] != px[x, y]) diff++
            }
        }
        assertTrue(diff > 0, "大陆/台湾标点规范应产生可见差异")
        println(">>> 大陆 vs 台湾标点规范像素差异 = $diff 像素（角位表生效）")
    }

    private fun renderVerticalOnly(
        m: TextMeasurer,
        layout: KhetiVerticalLayout,
        style: KhetiTextStyle,
        region: KhetiRegion,
    ): ImageBitmap {
        val pad = 28
        val titleH = 76
        val bmpW = layout.size.width + pad * 2
        val bmpH = titleH + layout.size.height + pad * 2
        val bitmap = ImageBitmap(bmpW, bmpH)
        CanvasDrawScope().draw(
            Density(1f), LayoutDirection.Ltr, Canvas(bitmap), Size(bmpW.toFloat(), bmpH.toFloat()),
        ) {
            drawRect(color = Color.White)
            drawKhetiVerticalText(
                measurer = m, layout = layout, style = style, color = Color.Black,
                density = Density(1f), region = region,
                left = pad.toFloat(), top = (titleH + pad).toFloat(),
            )
        }
        return bitmap
    }
}
