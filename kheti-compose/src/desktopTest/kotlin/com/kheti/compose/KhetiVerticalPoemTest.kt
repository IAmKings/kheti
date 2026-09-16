package com.kheti.compose

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import com.kheti.KhetiMetrics
import com.kheti.layout.KhetiVerticalEngine
import com.kheti.layout.layoutKhetiVerticalBlocks
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 竖排诗词必须保持与横排相同的层级：标题更大更重、元信息更小更淡、正文常规。
 * 回归背景：早期 `KhetiVerticalText` 只吃"一段文本 + 一个样式"，
 * 竖排时标题/作者/正文全被压成同一字号字重。
 */
class KhetiVerticalPoemTest {

    private val stanzas = listOf(
        listOf("李白乘舟将欲行，", "忽闻岸上踏歌声。"),
        listOf("桃花潭水深千尺，", "不及汪伦送我情。"),
    )

    private fun fonts() = KhetiFontFamilies(
        song = FontFamily.Serif,
        kai = FontFamily.Cursive,
        hei = FontFamily.SansSerif,
    )

    @Test
    fun 竖排块规格保持标题元信息正文的层级() {
        val fonts = fonts()
        val specs = buildKhetiVerticalPoemSpecs(
            stanzas = stanzas,
            title = "赠汪伦",
            meta = "〔唐〕李白（701—762）",
            flavor = KhetiFlavor.Classic,
            size = KhetiMetrics.Size.XLarge,
            fonts = fonts,
            colors = KhetiLightColors,
        )
        assertEquals(4, specs.size, "标题 + 元信息 + 两个诗节")

        // 标题：H2 24px、楷体、800
        assertEquals(24.sp, specs[0].style.fontSize, "标题应保持横排的 H2 字号")
        assertEquals(FontWeight(800), specs[0].style.fontWeight, "标题应保持加粗")
        assertEquals(fonts.kai, specs[0].style.fontFamily, "传统风格标题应为楷体")
        assertEquals(KhetiLightColors.ink, specs[0].color)

        // 元信息：小一号、次级颜色
        assertEquals(14.sp, specs[1].style.fontSize, "元信息应保持小一号")
        assertEquals(KhetiLightColors.inkSecondary, specs[1].color, "元信息应保持次级颜色")

        // 正文：x-large 20px、宋体、常规字重
        assertEquals(20.sp, specs[2].style.fontSize)
        assertEquals(fonts.song, specs[2].style.fontFamily, "正文应为宋体")
        assertEquals(FontWeight.Normal, specs[2].style.fontWeight)
    }

    @Test
    fun 竖排块按右到左排列且互不重叠() {
        val fonts = fonts()
        val specs = buildKhetiVerticalPoemSpecs(
            stanzas = stanzas,
            title = "赠汪伦",
            meta = "〔唐〕李白（701—762）",
            flavor = KhetiFlavor.Classic,
            size = KhetiMetrics.Size.XLarge,
            fonts = fonts,
            colors = KhetiLightColors,
        )
        val measurer = TextMeasurer(createFontFamilyResolver(), Density(1f), LayoutDirection.Ltr)
        val blocks = layoutKhetiVerticalBlocks(
            engine = KhetiVerticalEngine(measurer, Density(1f)),
            specs = specs,
            maxHeightPx = 800,
        ).blocks

        assertEquals(4, blocks.size)
        val xs = blocks.map { it.x }
        assertEquals(xs.sortedDescending(), xs, "块序必须右→左：$xs")
        for (i in 0 until blocks.size - 1) {
            val left = blocks[i].x
            val nextRight = blocks[i + 1].x + blocks[i + 1].layout.size.width
            assertTrue(
                left >= nextRight - 0.01f,
                "块 $i 与其左邻块重叠：left=$left 左邻右边界=$nextRight",
            )
        }
        // 标题块（最右）应因字号更大而列更宽
        assertTrue(
            blocks[0].layout.columnWidthPx > blocks[2].layout.columnWidthPx,
            "标题列宽应大于正文列宽（字号更大）",
        )
    }
}
