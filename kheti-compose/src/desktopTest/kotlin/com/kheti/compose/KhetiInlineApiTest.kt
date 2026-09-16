package com.kheti.compose

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import com.kheti.KhetiMetrics
import com.kheti.layout.KhetiAlignment
import com.kheti.layout.KhetiHang
import com.kheti.layout.KhetiInlineKind
import com.kheti.layout.KhetiInlineSpan
import com.kheti.layout.KhetiLang
import com.kheti.layout.KhetiTextStyle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * M8/D13 公共 API 层：行内区间必须按段落**平移到各段内下标**，语言随块透传。
 * 引擎层的效果解析已在 `M8InlineTest` 验证。
 */
class KhetiInlineApiTest {

    private val fonts = KhetiFontFamilies(
        song = FontFamily.Serif,
        kai = FontFamily.Serif,
        hei = FontFamily.SansSerif,
    )

    @Test
    fun 行内区间按段落平移并透传语言() {
        val text = "第一段。\n中文标记测试"
        val markStart = text.indexOf("标记")
        val specs = buildParagraphSpecs(
            text = text,
            style = KhetiTextStyle.of(KhetiMetrics.Size.Normal),
            alignment = KhetiAlignment.Start,
            hang = KhetiHang.Off,
            spacing = true,
            firstLineIndentEm = 0f,
            emphasis = emptyList(),
            inline = listOf(KhetiInlineSpan(markStart, markStart + 2, KhetiInlineKind.Mark)),
            lang = KhetiLang.Latin,
            color = Color.Unspecified,
        )
        assertEquals(2, specs.size, "应为两段")
        assertTrue(specs[0].inline.isEmpty(), "第一段不应带行内区间")
        val local = specs[1].inline.single()
        // 第二段文本为「中文标记测试」，「标记」在段内下标 2..4
        assertEquals("中文标记测试", specs[1].text)
        assertEquals(2, specs[1].text.indexOf("标记"), "段内定位应为 2")
        assertEquals(2, local.start, "区间应平移到段内下标")
        assertEquals(4, local.end)
        assertEquals(KhetiInlineKind.Mark, local.kind)
        assertEquals(KhetiLang.Latin, specs[1].lang, "语言应透传到块规格")
        assertEquals(KhetiLang.Latin, specs[0].lang, "语言应透传到所有块")
    }
}
