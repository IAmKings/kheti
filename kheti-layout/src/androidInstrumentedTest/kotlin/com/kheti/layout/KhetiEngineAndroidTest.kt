package com.kheti.layout

import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kheti.KhetiMetrics
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 在**真机（Minikin）**上验证与桌面端相同的规则不变量。
 *
 * 这里断言的是**与字体度量无关的量**（em 增量、行盒网格、悬挂），
 * 因此两端可以用不同字体得到相同结论——这正是"规则级跨平台等价"的可验证形式。
 */
@RunWith(AndroidJUnit4::class)
class KhetiEngineAndroidTest {

    private fun engine(): KhetiEngine {
        val resolver = createFontFamilyResolver(ApplicationProvider.getApplicationContext())
        return KhetiEngine(TextMeasurer(resolver, Density(1f), LayoutDirection.Ltr), Density(1f))
    }

    @Test
    fun 标点挤压与中西文间距在真机上落地为相同em增量() {
        val e = engine()
        val xlarge = KhetiTextStyle.of(KhetiMetrics.Size.XLarge)

        val squeeze = e.layout("借指纸。《汉书", xlarge, maxWidthPx = 2000, alignment = KhetiAlignment.Start)
        val line = squeeze.lines[0]
        val k = squeeze.text.indexOf('。')
        val expectedSqueeze = (-0.5f + xlarge.letterSpacingEm) * 20f
        assertEquals(
            expectedSqueeze,
            line.charX[k + 1] - (line.charX[k] + line.charAdvance[k]),
            0.01f,
            "。《 的净挤压应为 −0.5em + 字距",
        )

        val gap = e.layout("中文 abc 中文", xlarge, maxWidthPx = 2000, alignment = KhetiAlignment.Start)
        val g = gap.lines[0]
        val s = gap.text
        val a = s.indexOf('a')
        val c = s.indexOf('c')
        assertEquals(0.30f * 20f, g.charX[a] - (g.charX[a - 1] + g.charAdvance[a - 1]), 0.01f, "CJK→西文 ¼ 字宽 + 前字字距")
        assertEquals(0.25f * 20f, g.charX[c + 1] - (g.charX[c] + g.charAdvance[c]), 0.01f, "西文→CJK ¼ 字宽（gap 内字距 normal）")
    }

    @Test
    fun 真机上悬挂与行盒网格与桌面一致() {
        val e = engine()
        val style = KhetiTextStyle.of(KhetiMetrics.Size.XLarge)
        val poem = "李白乘舟将欲行，\n忽闻岸上踏歌声。\n桃花潭水深千尺，\n不及汪伦送我情。"
        val layout = e.layout(poem, style, maxWidthPx = 672, alignment = KhetiAlignment.Center, hang = KhetiHang.LineEnd)

        assertEquals(4, layout.lineCount, "硬换行应为 4 行")
        for (i in layout.lines.indices) {
            assertEquals(i * 30f, layout.getLineTop(i), 0.01f, "行盒应贴合 30px 网格（x-large）")
        }
        for ((i, line) in layout.lines.withIndex()) {
            assertTrue(line.hangingIndex >= 0, "第 $i 行行末标点应悬挂")
        }
        val xs = layout.lines.map { it.x }.distinct()
        assertEquals(1, xs.size, "四行字列应对齐：$xs")
        println(">>> Android 真机：行起点=${layout.lines[0].x} 行宽=${layout.lines[0].width} 行数=${layout.lineCount}")
    }
}
