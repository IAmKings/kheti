package com.kheti.layout

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
 * Phase 4 **浏览器对拍**：把我们的逐字几何与"真实赫蹏在真实 Chrome 里的渲染几何"逐字符比较。
 *
 * 参考数据由 `tools/reference-gen/browser-geometry.mjs` 生成
 * （上游 npm 包 `heti@0.9.6` 的 `heti.min.css` + `heti-addon.min.js` + 本机 Chrome 153
 * + 与 Kotlin 侧**同一个 TTF**），因此这是**渲染级**证据，是回答"还原了多少"的量化手段。
 *
 * ## 重要的适用范围（实测得出）
 * 参考字体 `lxgw_wenkai_regular.ttf` 是 CJK 子集：**ASCII 区仅覆盖 5/95 码位**。
 * 因此含西文的夹具在 Chrome 与 Skia 上会各自回退到**不同字体**，advance 必然不同
 * （实测累计可达 3.7px）—— 那是字体问题，不是规则问题。
 * 故本测试：
 * - 对**纯 CJK 夹具**做严格断言（同一字体，应亚像素一致；实测 0.01~0.03px）；
 * - 对含西文夹具只**记录**偏差并标注原因。
 *
 * 跨平台（Android/macOS/Web）要对齐西文几何，前提是打包一个**同时覆盖中西文**的字体
 * —— 这是本项目对齐任务的关键结论，已写入 docs/PRD.md。
 */
class BrowserGeometryParityTest {

    private val width = 672

    private fun referenceFile(): File {
        val candidates = listOf(
            File("../reference/browser-geometry.tsv"),
            File("reference/browser-geometry.tsv"),
            File("../../reference/browser-geometry.tsv"),
        )
        return candidates.firstOrNull { it.exists() }
            ?: error("找不到浏览器参考几何，请先运行 tools/reference-gen/browser-geometry.mjs")
    }

    private class RefChar(val ch: Char, val x: Float, val width: Float)

    private fun parse(line: String): Pair<String, List<RefChar>> {
        val cols = line.split('\t')
        val text = cols[0]
        val chars = cols[2].split(',').map { f ->
            val parts = f.split(':')
            RefChar(parts[0][0], parts[1].toFloat(), parts[2].toFloat())
        }
        return text to chars
    }

    private fun hasLatin(text: String) = text.any { it.code in 0x20..0x7E }

    @Test
    fun 逐字几何与浏览器渲染对齐() {
        val fontFile = File("../reference/fonts/lxgw_wenkai_regular.ttf")
        assertTrue(fontFile.exists(), "缺少参考字体 ${fontFile.absolutePath}")
        val family = FontFamily(androidx.compose.ui.text.platform.Font(fontFile))

        val measurer = TextMeasurer(createFontFamilyResolver(), Density(1f), LayoutDirection.Ltr)
        val engine = KhetiEngine(measurer, Density(1f))
        val style = KhetiTextStyle.of(KhetiMetrics.Size.Normal, family)

        val rows = referenceFile().readLines().filter { it.isNotBlank() }
        assertTrue(rows.size >= 5, "参考夹具过少：${rows.size}")

        var worstCovered = 0f
        var worstWhere = ""
        var total = 0
        var coveredFixtures = 0
        val report = ArrayList<String>()

        for (line in rows) {
            val (refText, refChars) = parse(line)
            val layout = engine.layout(
                text = refText,
                style = style,
                maxWidthPx = width,
                alignment = KhetiAlignment.Start,
                hang = KhetiHang.Off,
            )
            // 归一化文本必须一致（含"吞掉中西文之间空格"的行为）
            assertEquals(refText, layout.text, "归一化文本与浏览器不一致：$refText")
            assertEquals(1, layout.lineCount, "夹具应单行：$refText")

            val ourLine = layout.lines[0]
            var fixtureWorst = 0f
            for ((i, rc) in refChars.withIndex()) {
                if (rc.ch != layout.text[i]) continue
                val ourX = ourLine.x + ourLine.charX[i]
                val d = abs(ourX - rc.x)
                total++
                if (d > fixtureWorst) fixtureWorst = d
                if (!hasLatin(refText)) {
                    if (d > worstCovered) {
                        worstCovered = d
                        worstWhere =
                            "「${layout.text}」第 $i 字 '${rc.ch}'：kheti=${"%.2f".format(ourX)} 浏览器=${"%.2f".format(rc.x)}"
                    }
                }
            }
            if (!hasLatin(refText)) coveredFixtures++
            report += "「$refText」最大偏差 %.2fpx%s".format(
                fixtureWorst,
                if (hasLatin(refText)) "（含西文，受字体回退影响，仅记录）" else "",
            )
        }

        println(">>> 浏览器对拍：${rows.size} 条夹具 / ${total} 个字符；纯 CJK 夹具 $coveredFixtures 条做严格断言")
        report.forEach { println("    $it") }
        println(">>> 纯 CJK 最大偏差 = %.2fpx（$worstWhere）".format(worstCovered))

        // 同一字体下，纯 CJK 应达到亚像素一致（实测 0.01~0.03px）
        assertTrue(worstCovered < 0.1f, "纯 CJK 逐字几何偏差过大：$worstWhere")
    }
}
