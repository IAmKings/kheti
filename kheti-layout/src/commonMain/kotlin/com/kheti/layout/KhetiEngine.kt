package com.kheti.layout

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.sp
import com.kheti.AdjustmentPlanner
import com.kheti.KhetiMetrics
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * kheti 布局引擎：**唯一**负责把赫蹏规则变成几何的地方。
 *
 * 流程（对应 ADR-001 的"方案 B"）：
 * 1. `AdjustmentPlanner` 归一化文本并算出逐字 leading/trailing 增量；
 * 2. 平台度量给出**自然 advance**（我们只用它作为字形宽度来源，不用它的 letterSpacing）；
 * 3. 增量按累加方式改写每个字符的 x；
 * 4. 断行、对齐、悬挂都在我们的坐标系里完成。
 *
 * 平台只贡献"字形有多宽"，几何完全由 kheti 决定 —— 这是跨平台一致的根源。
 */
class KhetiEngine(
    private val measurer: TextMeasurer,
    private val density: Density,
) {

    fun layout(
        text: String,
        style: KhetiTextStyle = KhetiTextStyle(),
        maxWidthPx: Int,
        alignment: KhetiAlignment = KhetiAlignment.Start,
        hang: KhetiHang = KhetiHang.Off,
        spacing: Boolean = true,
        /** 首行缩进（em）。上游 `.heti--ancient p { text-indent: 2em }`。 */
        firstLineIndentEm: Float = 0f,
        /** 行间注（对应上游 `<ruby>`）；下标基于 [text] 原文。 */
        ruby: List<KhetiRuby> = emptyList(),
        /** 行间注版式：挤进行盒（保持网格）或占据行盒上方。 */
        rubyMode: KhetiRubyMode = KhetiRubyMode.Inline,
        /** 注音相对基文的字号比例（浏览器默认 50%）。 */
        rubyScale: Float = 0.5f,
        /** 行内元素样式区间（`a`/`abbr`/`u`/`del`/`mark`/`code`…）。 */
        inline: List<KhetiInlineSpan> = emptyList(),
        /** 容器语言：西文容器字距归零（上游 `[lang=en-US]`）。 */
        lang: KhetiLang = KhetiLang.Zh,
    ): KhetiLayout {
        val plan = if (spacing) AdjustmentPlanner.plan(text) else AdjustmentPlanner.identity(text)
        val s = plan.text
        val n = s.length
        val ts = style.toTextStyle()
        val fontSizePx = style.fontSizePx(density)
        val lineHeightPx = style.lineHeightPx(density)

        if (n == 0) return KhetiLayout("", emptyList(), IntSize(0, 0), plan, lineHeightPx)

        // ---- 逐字增量（px）----
        val inGap = BooleanArray(n)
        for (r in plan.gapRanges) for (i in r) if (i in 0 until n) inGap[i] = true
        // 西文容器（lang=Latin）字距为 0（上游 `[lang=en-US]{letter-spacing:normal}`）
        val baseLetterSpacing = if (lang == KhetiLang.Latin) 0f else style.letterSpacingEm
        val leadPx = FloatArray(n)
        val trailPx = FloatArray(n)
        for (i in 0 until n) {
            leadPx[i] = plan.leadingEm[i] * fontSizePx
            // 上游 `.heti heti-spacing { letter-spacing: normal }` 是**直接匹配该元素的规则**，
            // 而"匹配规则优先于继承"——因此即便 x-large 的 0.05em 也不会作用于 gap 区间。
            // （易错点：这不是优先级之争，继承值根本不参与比较。）
            val ls = if (inGap[i]) 0f else baseLetterSpacing
            trailPx[i] = (plan.trailingEm[i] + ls) * fontSizePx
        }

        val bounded = maxWidthPx in 1 until Int.MAX_VALUE
        val limit = if (bounded) maxWidthPx else Int.MAX_VALUE
        val indentPx = (firstLineIndentEm * fontSizePx).roundToInt()
        val inlineEffects = KhetiInlineEffects(n, inline, plan)

        // 行间注：原文下标 → 归一化下标（归一化会吞掉中西文之间的空格）
        val plannedRuby = ruby.mapNotNull { rb ->
            if (n == 0) return@mapNotNull null
            val s0 = rb.start.coerceIn(0, text.length - 1)
            val e0 = (rb.end - 1).coerceIn(0, text.length - 1)
            val ps = plan.plannedIndex(s0)
            val pe = plan.plannedIndex(e0) + 1
            if (pe > ps && ps in 0 until n) MappedRuby(ps, minOf(pe, n), rb.annotation) else null
        }

        // 行间注需要在块顶预留空间：上游 inline-flex/column-reverse 会让注音
        // 溢出到行盒**之上**（落进行间空白），首行若不预留就会被裁掉或压住上一块。
        val rubyFontSize0 = fontSizePx * rubyScale
        val topReserve = when {
            plannedRuby.isEmpty() -> 0f
            rubyMode == KhetiRubyMode.Inline -> rubyFontSize0 * 0.75f
            // 块模式：基文墨迹贴近行盒顶，注音几乎整份落在行盒之上，预留要更宽
            else -> rubyFontSize0 + fontSizePx * 0.25f
        }

        // ---- 断行 ----
        val ranges = breakLines(s, ts, limit, leadPx, trailPx, hang, n, indentPx)

        // ---- 逐行几何 + 对齐 ----
        val lines = ArrayList<KhetiLine>(ranges.size)
        var naturalMax = 0f
        var y = topReserve
        for ((index, r) in ranges.withIndex()) {
            val geom = measureLine(s, r.first, r.last + 1, ts, leadPx, trailPx, hang, inlineEffects.scale)
            naturalMax = maxOf(naturalMax, geom.width)
            val len = r.last - r.first + 1

            val lastLine = index == ranges.lastIndex
            // 两端对齐：余量只分配到**可分配间隙**（汉字之间、词与词之间）。
            // 浏览器与 clreq 都不在西文单词内部拆字拉开——早期实现按每个字均分，
            // 会把 "clreq" 拉成 "c l r e q"，是明显的排版错误。
            val distributable = if (alignment == KhetiAlignment.Justify && !lastLine && len > 1 && bounded) {
                (1 until len).count { k -> !isIntraWord(s[r.first + k - 1], s[r.first + k]) }
            } else {
                0
            }
            val extra = if (distributable > 0) {
                ((limit - geom.width) / distributable).coerceAtLeast(0f)
            } else {
                0f
            }
            val gapExtraBefore = FloatArray(len)
            var distributed = 0f
            for (k in 1 until len) {
                if (!isIntraWord(s[r.first + k - 1], s[r.first + k])) distributed += extra
                gapExtraBefore[k] = distributed
            }
            val drawnX = FloatArray(len) { k -> geom.x[k] + gapExtraBefore[k] }
            val contentWidth = geom.width + distributed
            // 首行缩进只在"左对齐类"下施加（诗词居中版式不缩进，与上游 `--poetry` 一致）
            val indent = if (index == 0 && alignment != KhetiAlignment.Center && alignment != KhetiAlignment.End) {
                indentPx.toFloat()
            } else {
                0f
            }
            val offsetX = indent + when (alignment) {
                KhetiAlignment.Start, KhetiAlignment.Justify -> 0f
                KhetiAlignment.Center -> ((limit.takeIf { bounded }?.toFloat() ?: contentWidth) - geom.width) / 2f
                KhetiAlignment.End -> (limit.takeIf { bounded }?.toFloat() ?: contentWidth) - geom.width
            }

            lines += KhetiLine(
                start = r.first,
                end = r.last + 1,
                top = y,
                width = contentWidth,
                x = offsetX,
                segments = buildSegments(r.first, r.last + 1, geom, drawnX, offsetX, inlineEffects.scale),
                charX = drawnX,
                charAdvance = geom.advances,
                hangingIndex = geom.hangingIndex,
                ruby = if (plannedRuby.isEmpty()) {
                    emptyList()
                } else {
                    computeLineRuby(
                        planned = plannedRuby,
                        lineStart = r.first,
                        lineEnd = r.last + 1,
                        geom = geom,
                        drawnX = drawnX,
                        offsetX = offsetX,
                        lineTop = y,
                        ts = ts,
                        fontSizePx = fontSizePx,
                        lineHeightPx = lineHeightPx,
                        mode = rubyMode,
                        scale = rubyScale,
                    )
                },
            )
            y += lineHeightPx
        }

        val size = IntSize(
            width = if (bounded) limit else naturalMax.toInt(),
            // 高度必须包含行间注在块顶预留的空间，否则首行注音会被裁掉
            height = (topReserve + ranges.size * lineHeightPx).toInt(),
        )
        return KhetiLayout(s, lines, size, plan, lineHeightPx, inlineEffects.takeIf { !it.isEmpty })
    }

    // ------------------------------------------------------------------ 断行

    private fun breakLines(
        s: String,
        ts: TextStyle,
        limit: Int,
        leadPx: FloatArray,
        trailPx: FloatArray,
        hang: KhetiHang,
        n: Int,
        indentPx: Int,
    ): List<IntRange> {
        val out = ArrayList<IntRange>()
        var start = 0
        while (start < n) {
            // 首行可用宽度要扣掉缩进（上游 text-indent 会挤掉首行内容）
            val avail = if (start == 0) (limit - indentPx).coerceAtLeast(1) else limit
            // 平台给断行建议（它懂 ICU 的 CJK/拉丁断行规则），我们再按"调整后宽度"校验
            val p = measurer.measure(
                AnnotatedString(s.substring(start)),
                ts,
                constraints = if (avail == Int.MAX_VALUE) Constraints() else Constraints(maxWidth = avail),
            )
            var end = start + p.getLineEnd(0, visibleEnd = true)
            if (end <= start) end = start + 1

            val hard = s.indexOf('\n', start)
            if (hard in start until end) end = hard

            // 缩短：调整后宽度必须放得下
            while (end > start + 1 && measureLine(s, start, end, ts, leadPx, trailPx, hang).width > avail) end--

            // 贪心延长：负挤压/悬挂可能让下一个字也放得下；不拆西文单词，不越过硬换行
            while (end < n && s[end] != '\n' && !isLatinLetter(s[end]) &&
                measureLine(s, start, end + 1, ts, leadPx, trailPx, hang).width <= avail
            ) {
                end++
            }

            out += start until end
            start = end
            if (start < n && s[start] == '\n') start++ // 硬换行字符本身不属于任何行
        }
        return out
    }

    private fun isLatinLetter(ch: Char): Boolean =
        (ch in 'A'..'Z') || (ch in 'a'..'z') || ch.code in 0x00C0..0x024F

    // ------------------------------------------------------------ 单行度量

    private class LineGeom(
        val x: FloatArray,
        val advances: FloatArray,
        val width: Float,
        val hangingIndex: Int,
    )

    private fun measureLine(
        s: String,
        start: Int,
        end: Int,
        ts: TextStyle,
        leadPx: FloatArray,
        trailPx: FloatArray,
        hang: KhetiHang,
        /** 逐字符缩放（上标/下标/小号）；null 表示全为 1。 */
        scale: FloatArray? = null,
    ): LineGeom {
        val len = end - start
        val p = measurer.measure(AnnotatedString(s.substring(start, end)), ts, constraints = Constraints())
        val adv = FloatArray(len)
        // 逐字 advance 按**同文种连续段**分别测量。
        // 原因（浏览器对拍实测）：把西文夹在汉字串里一起测量时，Skia 给出的西文 advance
        // 与"单独测西文段"不一致（每个字母偏窄约 0.2~0.5px，并沿行累积），
        // 而 Chrome 是按西文段自身度量。按段测量后两端一致。
        var k = 0
        while (k < len) {
            val sc = scale?.get(start + k) ?: 1f
            if (sc != 1f) {
                // 缩放字（sup/sub/small）：必须按**缩放后的样式**单独度量，
                // 否则 advance 用的是原字号宽度，后续文字位置全错。
                val one = s.substring(start + k, start + k + 1)
                val styled = ts.copy(fontSize = ts.fontSize * sc)
                adv[k] = measurer.measure(AnnotatedString(one), styled, constraints = Constraints())
                    .getHorizontalPosition(1, true)
                k++
                continue
            }
            val cls = scriptClass(s[start + k])
            var j = k
            while (j < len && scriptClass(s[start + j]) == cls && (scale?.get(start + j) ?: 1f) == 1f) j++
            if (j - k == 1) {
                adv[k] = p.getHorizontalPosition(k + 1, true) - p.getHorizontalPosition(k, true)
            } else {
                val runText = s.substring(start + k, start + j)
                val runP = measurer.measure(AnnotatedString(runText), ts, constraints = Constraints())
                for (m in k until j) {
                    adv[m] = runP.getHorizontalPosition(m - k + 1, true) - runP.getHorizontalPosition(m - k, true)
                }
            }
            k = j
        }
        val x = FloatArray(len)
        var acc = 0f
        for (k in 0 until len) {
            acc += leadPx[start + k]
            x[k] = acc
            acc += adv[k] + trailPx[start + k]
        }
        val hanging = if (hang == KhetiHang.LineEnd && len > 0 && KhetiMetrics.isHangable(s[end - 1])) end - 1 else -1
        // 行宽不含末字的 trailing（其后无字），悬挂标点的 advance 也不计
        val width = x[len - 1] + if (hanging >= 0) 0f else adv[len - 1]
        return LineGeom(x, adv, width, hanging)
    }

    // ------------------------------------------------------------ 行间注

    private class MappedRuby(val start: Int, val end: Int, val annotation: String)

    /**
     * 计算落在某一行上的注音几何（对应上游 `ruby`/`rt`）。
     *
     * - 注音在基文区间上**居中**（浏览器对 `<ruby>` 的默认行为，也是上游 `rt { text-align: center }`）。
     * - [KhetiRubyMode.Inline]：`rt { margin-bottom: -0.25em; line-height: 1 }` —— 注音底边压进
     *   基文上方 ¼ 字宽，整体不超出 1.5em 行盒，因此**不破坏垂直网格**。
     * - [KhetiRubyMode.Block]：注音完整位于基文上方，由 2.25 倍行高提供空间。
     */
    private fun computeLineRuby(
        planned: List<MappedRuby>,
        lineStart: Int,
        lineEnd: Int,
        geom: LineGeom,
        drawnX: FloatArray,
        offsetX: Float,
        lineTop: Float,
        ts: TextStyle,
        fontSizePx: Float,
        lineHeightPx: Float,
        mode: KhetiRubyMode,
        scale: Float,
    ): List<KhetiRubyGlyph> {
        val out = ArrayList<KhetiRubyGlyph>(planned.size)
        val rubyFontSize = fontSizePx * scale
        // 注意：rubyFontSize 是**像素**；TextUnit 必须按当前 density 换算回 sp，
        // 否则在高密度设备上会被再乘一次 density（4× 字号 → 注音盖住整行汉字）。
        val rubyUnit = with(density) { rubyFontSize.toSp() }
        val rubyStyle = ts.copy(fontSize = rubyUnit, lineHeight = rubyUnit)
        // 基文在行盒内的顶部：
        // - Inline：上游是 `inline-flex; column-reverse; height: 1.5em`，注音被挤到**行盒之上**
        //   （落入行间空白），底边压住行盒顶 0.25em（rt 的 -0.25em 负外边距）。
        //   若把注音画进行盒内部，就会直接压在汉字上——这正是要避免的。
        // - Block：注音要落在**基文墨迹之上**。注意不能假设基文"居于行盒中央"：
        //   Compose 把额外行距放在下方（CSS 是上下均分半行距），实测墨迹贴着行盒顶，
        //   按"居中"估算会让注音与汉字完全重叠（真机已复现）。
        val baseTop = when (mode) {
            KhetiRubyMode.Inline -> lineTop + lineHeightPx - fontSizePx
            KhetiRubyMode.Block -> {
                val probe = measurer.measure(AnnotatedString("字"), ts, constraints = Constraints())
                lineTop + probe.getBoundingBox(0).top
            }
        }
        for (rs in planned) {
            val s = maxOf(rs.start, lineStart)
            val e = minOf(rs.end, lineEnd)
            if (e <= s) continue
            val k0 = s - lineStart
            val k1 = e - 1 - lineStart
            val xStart = offsetX + drawnX[k0]
            val xEnd = offsetX + drawnX[k1] + geom.advances[k1]
            val w = measurer.measure(
                AnnotatedString(rs.annotation),
                rubyStyle,
                constraints = Constraints(),
            ).size.width.toFloat()
            val yTop = when (mode) {
                // 行盒之上，底边压住行盒顶 ¼（注音自身的 em）
                KhetiRubyMode.Inline -> lineTop + rubyFontSize * 0.25f - rubyFontSize
                // 行盒内、基文之上
                KhetiRubyMode.Block -> baseTop - rubyFontSize - fontSizePx * 0.05f
            }
            out += KhetiRubyGlyph(
                baseStart = s,
                baseEnd = e,
                annotation = rs.annotation,
                x = (xStart + xEnd) / 2f - w / 2f,
                y = yTop,
                width = w,
                fontSizePx = rubyFontSize,
            )
        }
        return out
    }

    // -------------------------------------------------------------- 分段

    /**
     * 把一行切成最少的可绘制段：段内字符保持原生连续（保留 shaping），
     * 只在**间距被改动**处断开。
     */
    private fun buildSegments(
        start: Int,
        end: Int,
        geom: LineGeom,
        drawnX: FloatArray,
        offsetX: Float,
        /** 逐字符缩放：缩放变化处必须切段，否则绘制会统一用同一个字号。 */
        scale: FloatArray? = null,
    ): List<KhetiSegment> {
        val len = end - start
        if (len == 0) return emptyList()
        val cuts = BooleanArray(len)
        cuts[0] = true
        for (k in 1 until len) {
            // 以**实际绘制坐标**判断连续性：两端对齐在两字之间加了余量时才切段，
            // 西文单词内部因此仍保持一次绘制（保留 shaping/kerning）。
            val gap = (drawnX[k] - drawnX[k - 1]) - geom.advances[k - 1]
            cuts[k] = abs(gap) > 0.001f ||
                (scale != null && scale[start + k] != scale[start + k - 1])
        }
        val out = ArrayList<KhetiSegment>()
        var segStart = 0
        for (k in 1..len) {
            if (k == len || cuts[k]) {
                out += KhetiSegment(
                    start = start + segStart,
                    end = start + k,
                    x = offsetX + drawnX[segStart],
                )
                segStart = k
            }
        }
        return out
    }

    /** 判断两字符是否处于同一个西文/数字词内部（词内不应被两端对齐拆开）。 */
    private fun isIntraWord(a: Char, b: Char): Boolean {
        fun token(c: Char): Boolean = c in 'A'..'Z' || c in 'a'..'z' || c in '0'..'9' ||
            c == '\'' || c == '’' || c == '-' || c == '.' || c == '@' || c == '_'
        return token(a) && token(b)
    }

    /**
     * 文种分类：0 = 汉字/全角（含 CJK 标点），1 = ASCII 可打印（西文/数字/半角符号），2 = 其它。
     * 用于把一行切成"同文种连续段"分别度量。
     */
    private fun scriptClass(ch: Char): Int = when {
        ch.code in 0x2E80..0x9FFF -> 0
        ch.code in 0xF900..0xFAFF -> 0
        ch.code in 0xFE30..0xFE4F -> 0
        ch.code in 0xFF00..0xFFEF -> 0
        ch.code in 0x3000..0x303F -> 0
        ch.code in 0x20..0x7E -> 1
        else -> 2
    }
}
