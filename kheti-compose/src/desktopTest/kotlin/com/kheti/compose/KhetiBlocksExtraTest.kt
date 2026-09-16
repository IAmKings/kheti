package com.kheti.compose

import com.kheti.KhetiMetrics
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * D12 块级元素：几何常量与列表标记。
 *
 * 常量逐条对应上游 SCSS，因此这里断言的是"移植没抄错"。
 */
class KhetiBlocksExtraTest {

    @Test
    fun 中文数字序号() {
        assertEquals("一", hanNumeral(1))
        assertEquals("九", hanNumeral(9))
        assertEquals("十", hanNumeral(10))
        assertEquals("十一", hanNumeral(11))
        assertEquals("二十", hanNumeral(20))
        assertEquals("二十五", hanNumeral(25))
        assertEquals("九十九", hanNumeral(99))
    }

    @Test
    fun 小写罗马数字序号() {
        assertEquals("i", lowerRoman(1))
        assertEquals("iv", lowerRoman(4))
        assertEquals("ix", lowerRoman(9))
        assertEquals("xiv", lowerRoman(14))
        assertEquals("mcmxc", lowerRoman(1990))
    }

    @Test
    fun 列表标记与上游list_style_type对应() {
        assertEquals("• ", khetiListMarker(1, KhetiListMarker.Bullet))
        assertEquals("3. ", khetiListMarker(3, KhetiListMarker.Decimal))
        // heti-list-han → cjk-ideographic
        assertEquals("三、", khetiListMarker(3, KhetiListMarker.HanIdeographic))
        // heti-list-latin 顶层 → upper-latin
        assertEquals("C. ", khetiListMarker(3, KhetiListMarker.UpperLatin))
        // heti-list-latin 第二层 → lower-roman
        assertEquals("iv. ", khetiListMarker(4, KhetiListMarker.LowerRoman))
    }

    @Test
    fun 块级几何常量与上游SCSS一致() {
        // blockquote：margin-block 0.5 / 1 网格；margin-inline 2em；padding-inline 1em
        assertEquals(KhetiMetrics.GRID_UNIT * 0.5f, KhetiBlocks.BLOCKQUOTE_MARGIN_TOP)
        assertEquals(KhetiMetrics.GRID_UNIT, KhetiBlocks.BLOCKQUOTE_MARGIN_BOTTOM)
        assertEquals(2f, KhetiBlocks.BLOCKQUOTE_MARGIN_INLINE_EM)
        assertEquals(1f, KhetiBlocks.BLOCKQUOTE_PADDING_INLINE_EM)
        // hr：宽 30%、高 1px、上下 2 网格
        assertEquals(0.30f, KhetiBlocks.HR_WIDTH_FRACTION)
        assertEquals(1f, KhetiBlocks.HR_HEIGHT)
        assertEquals(48f, KhetiBlocks.HR_MARGIN_BLOCK)
        // 背景 alpha = .054（hsla(0,0%,0%,.054)）
        assertEquals(0.054f, KhetiBlocks.SURFACE_ALPHA)
    }

    @Test
    fun 标题与表格常量取自上游编译产物() {
        // _heading.scss：基础字重 600（800 只属诗词/古文 modifier）
        assertEquals(600, KhetiBlocks.HEADING_WEIGHT)
        // 所有标题 margin-block-start: 24px；h2–h6 底边距 12px；h1 为 24px
        assertEquals(24f, KhetiBlocks.HEADING_MARGIN_TOP)
        assertEquals(12f, KhetiBlocks.HEADING_MARGIN_BOTTOM)
        assertEquals(24f, KhetiBlocks.HEADING_H1_MARGIN_BOTTOM)
        // h1–h3 额外字距 .05em
        assertEquals(0.05f, KhetiBlocks.HEADING_LETTER_SPACING_LARGE_EM)
        // _table.scss：margin-block 12/24；单元格内边距 6/8；边框 #ccc
        assertEquals(12f, KhetiBlocks.TABLE_MARGIN_TOP)
        assertEquals(24f, KhetiBlocks.TABLE_MARGIN_BOTTOM)
        assertEquals(6f, KhetiBlocks.TABLE_CELL_PADDING_BLOCK)
        assertEquals(8f, KhetiBlocks.TABLE_CELL_PADDING_INLINE)
        assertEquals(androidx.compose.ui.graphics.Color(0xFFCCCCCC), KhetiBlocks.TABLE_BORDER_LIGHT)
        // caption-side: bottom
        assertTrue(KhetiBlocks.TABLE_CAPTION_BELOW)
        assertEquals(14f, KhetiBlocks.CAPTION_FONT_SIZE)
        assertEquals(24f, KhetiBlocks.CAPTION_LINE_HEIGHT)
    }

    @Test
    fun 脚注常量取自上游heti_fn() {
        // .heti-fn { margin-block-start: 59px; border-block-start: 1px solid; font-size: 14px;
        //            font-family: 黑体; line-height: 24px; ol { margin-block-start: 0.5 网格 } }
        assertEquals(59f, KhetiBlocks.FOOTNOTE_MARGIN_TOP)
        assertEquals(14f, KhetiBlocks.FOOTNOTE_FONT_SIZE)
        assertEquals(24f, KhetiBlocks.FOOTNOTE_LINE_HEIGHT)
        assertEquals(12f, KhetiBlocks.FOOTNOTE_LIST_MARGIN_TOP)
        assertEquals(androidx.compose.ui.graphics.Color(0xFFCCCCCC), KhetiBlocks.FOOTNOTE_BORDER_LIGHT)
        assertEquals(androidx.compose.ui.graphics.Color(0xFF404040), KhetiBlocks.FOOTNOTE_BORDER_DARK)
        // li:target 高亮：hsl(210,100%,93%) / hsl(210,40%,38%)
        assertEquals(androidx.compose.ui.graphics.Color(0xFFDBEDFF), KhetiBlocks.FOOTNOTE_HIGHLIGHT_LIGHT)
        assertEquals(androidx.compose.ui.graphics.Color(0xFF3A6188), KhetiBlocks.FOOTNOTE_HIGHLIGHT_DARK)
    }

    @Test
    fun 标题档与上游字号表一致() {
        assertEquals(32f, KhetiMetrics.Heading.H1.fontSize)
        assertEquals(24f, KhetiMetrics.Heading.H2.fontSize)
        assertEquals(36f, KhetiMetrics.Heading.H2.lineHeightPx)
        assertEquals(14f, KhetiMetrics.Heading.H6.fontSize)
        // 标题用楷体 + 800 字重（传统风格）
        assertEquals(
            androidx.compose.ui.text.font.FontWeight(800),
            com.kheti.layout.KhetiTextStyle.heading(
                KhetiMetrics.Heading.H2,
                androidx.compose.ui.text.font.FontFamily.Serif,
            ).fontWeight,
        )
    }
}
