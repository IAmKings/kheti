package com.kheti.compose

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
import com.kheti.layout.KhetiEngine
import com.kheti.layout.KhetiHang
import com.kheti.layout.KhetiAlignment
import com.kheti.layout.drawKhetiText
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * `kheti-compose` 公共 API 的桌面验收：
 * 走的是**与 composable 完全相同的纯函数路径**（`khetiPoemSpecs` → `layoutKhetiBlocks` → `drawKhetiText`），
 * 因此这里的结论对 UI 同样成立。
 */
class KhetiComposeRenderTest {

    private val poem = listOf(
        listOf("李白乘舟将欲行，", "忽闻岸上踏歌声。", "桃花潭水深千尺，", "不及汪伦送我情。"),
    )

    private fun families(): KhetiFontFamilies {
        fun load(name: String, fallback: FontFamily): FontFamily {
            val f = File("../reference/fonts/$name")
            return if (f.exists()) FontFamily(androidx.compose.ui.text.platform.Font(f)) else fallback
        }
        return KhetiFontFamilies(
            song = load("lxgw_neozhisong_screen.ttf", FontFamily.Serif),
            kai = load("lxgw_wenkai_regular.ttf", FontFamily.Serif),
            hei = FontFamily.SansSerif,
        )
    }

    private fun engine(): KhetiEngine {
        val measurer = TextMeasurer(createFontFamilyResolver(), Density(1f), LayoutDirection.Ltr)
        return KhetiEngine(measurer, Density(1f))
    }

    // ------------------------------------------------------------ 结构断言

    @Test
    fun 诗词规格为标题加元信息加诗节且硬换行不额外分段() {
        val fonts = families()
        val specs = khetiPoemSpecs(
            stanzas = poem,
            title = "赠汪伦",
            meta = "〔唐〕李白",
            flavor = KhetiFlavor.Classic,
            size = KhetiMetrics.Size.XLarge,
            hang = KhetiHang.LineEnd,
            fonts = fonts,
            colors = KhetiLightColors,
        )
        assertEquals(3, specs.size, "应为 标题 + 元信息 + 诗节 三个块")
        // 传统风格：标题楷体、正文宋体
        assertEquals(fonts.kai, specs[0].style.fontFamily, "标题应为楷体")
        assertEquals(fonts.song, specs[2].style.fontFamily, "正文应为宋体")
        assertEquals(KhetiAlignment.Center, specs[2].alignment, "诗词应居中")
        assertEquals(KhetiHang.LineEnd, specs[2].hang, "诗词行末标点应悬挂")

        val blocks = layoutKhetiBlocks(engine(), specs, widthPx = 672)
        val stanza = blocks.blocks.last().layout
        assertEquals(4, stanza.lineCount, "四条诗句应为四行（硬换行）")
        for (line in stanza.lines) {
            assertTrue(line.hangingIndex >= 0, "每行行末标点都应悬挂")
        }
        // 四行字数相同 → 字列必须对齐
        val xs = stanza.lines.map { it.x }.distinct()
        assertEquals(1, xs.size, "四行应共用同一起点（悬挂在字列之外）：$xs")
    }

    @Test
    fun 段间距按CSS外边距折叠为24px且首元素顶边距清零() {
        val fonts = families()
        val style = KhetiTextStylePreview(fonts)
        val specs = buildParagraphSpecs(
            text = "第一段。\n第二段。",
            style = style,
            alignment = KhetiAlignment.Start,
            hang = KhetiHang.Off,
            spacing = true,
            firstLineIndentEm = 0f,
            emphasis = emptyList(),
            color = Color.Unspecified,
        )
        assertEquals(2, specs.size)
        val blocks = layoutKhetiBlocks(engine(), specs, widthPx = 672)
        val (a, b) = blocks.blocks
        assertEquals(0f, a.top, 0.01f, "首元素顶边距应被清零（上游 :first-child 规则）")
        assertEquals(
            a.layout.size.height + 24f,
            b.top,
            0.5f,
            "段间距应为折叠后的 24px（max(24, 12)）",
        )
    }

    @Test
    fun 古文首行缩进两字宽() {
        val e = engine()
        val style = com.kheti.layout.KhetiTextStyle.of(KhetiMetrics.Size.Normal)
        val noIndent = e.layout("先帝创业未半而中道崩殂", style, 672, KhetiAlignment.Start)
        val indented = e.layout(
            "先帝创业未半而中道崩殂",
            style,
            672,
            KhetiAlignment.Start,
            firstLineIndentEm = 2f,
        )
        assertEquals(0f, noIndent.lines[0].x, 0.01f)
        assertEquals(32f, indented.lines[0].x, 0.5f, "首行应缩进 2em = 32px（16px 字号）")
    }

    // ------------------------------------------------------------ 渲染证据

    @Test
    fun 用公共API渲染诗词为PNG() {
        val fonts = families()
        val measurer = TextMeasurer(createFontFamilyResolver(), Density(1f), LayoutDirection.Ltr)
        val e = KhetiEngine(measurer, Density(1f))
        val colors = KhetiLightColors

        val specs = khetiPoemSpecs(
            stanzas = poem,
            title = "赠汪伦",
            meta = "〔唐〕李白",
            flavor = KhetiFlavor.Classic,
            size = KhetiMetrics.Size.XLarge,
            hang = KhetiHang.LineEnd,
            fonts = fonts,
            colors = colors,
        )
        val blockLayout = layoutKhetiBlocks(e, specs, widthPx = 672)

        val pad = 40
        val bitmap = ImageBitmap(blockLayout.width, blockLayout.height + pad * 2)
        val canvas = Canvas(bitmap)
        CanvasDrawScope().draw(
            Density(1f),
            LayoutDirection.Ltr,
            canvas,
            Size(bitmap.width.toFloat(), bitmap.height.toFloat()),
        ) {
            drawRect(color = Color.White)
            for (b in blockLayout.blocks) {
                drawKhetiText(
                    measurer = measurer,
                    layout = b.layout,
                    style = b.style,
                    color = b.color,
                    density = Density(1f),
                    topOffset = b.top + pad,
                    emphasis = b.emphasis,
                )
            }
        }

        val out = File("../reference/poem-kheti-api.png")
        out.writeBytes(
            (Image.makeFromBitmap(bitmap.asSkiaBitmap()).encodeToData(EncodedImageFormat.PNG)
                ?: error("PNG 编码失败")).bytes
        )
        println(">>> 公共 API 渲染证据：${out.absolutePath}（${out.length()} 字节，画布 ${bitmap.width}×${bitmap.height}）")

        // 像素自检：四行诗句墨迹应彼此对齐（字列对齐），且标点悬挂在列外
        val px = bitmap.toPixelMap()
        val stanza = blockLayout.blocks.last()
        fun bandInk(i: Int): IntRange? {
            val top = (stanza.top + pad + i * stanza.layout.lineHeightPx).toInt()
            val bottom = (top + stanza.layout.lineHeightPx).toInt()
            var min = Int.MAX_VALUE
            var max = Int.MIN_VALUE
            for (y in top until bottom.coerceAtMost(bitmap.height)) {
                for (x in 0 until bitmap.width) {
                    if (px[x, y].alpha > 0.05f && px[x, y].red < 0.9f) {
                        if (x < min) min = x
                        if (x > max) max = x
                    }
                }
            }
            return if (max < min) null else min..max
        }
        val inks = (0 until 4).map { bandInk(it) ?: error("第 $it 行无墨迹") }
        val lefts = inks.map { it.first }.distinct()
        assertEquals(1, lefts.size, "四行墨迹左边界应对齐：${inks.map { it.first }}")
        val line = stanza.layout.lines[0]
        assertTrue(
            inks[0].last > line.x + line.width,
            "行末标点应悬挂到内容宽度之外：ink=${inks[0].last} contentRight=${line.x + line.width}",
        )
        println(">>> 四行 ink=$inks（字列对齐），内容右边界=${(line.x + line.width).toInt()}")
    }

    /** 便于测试构造正文样式。 */
    private fun KhetiTextStylePreview(fonts: KhetiFontFamilies) =
        com.kheti.layout.KhetiTextStyle.body(KhetiMetrics.Size.Normal, KhetiFlavor.Serif, fonts)
}
