package com.kheti.sample

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kheti.KhetiMetrics
import com.kheti.compose.KhetiColors
import com.kheti.compose.KhetiDarkColors
import com.kheti.compose.KhetiFlavor
import com.kheti.compose.KhetiFontFamilies
import com.kheti.compose.KhetiLightColors
import com.kheti.compose.KhetiPoem
import com.kheti.compose.KhetiText
import com.kheti.compose.KhetiBlockquote
import com.kheti.compose.KhetiColumns
import com.kheti.compose.KhetiFigure
import com.kheti.compose.KhetiFootnotes
import com.kheti.compose.KhetiHeading
import com.kheti.compose.KhetiHr
import com.kheti.compose.KhetiList
import com.kheti.compose.KhetiListMarker
import com.kheti.compose.KhetiPre
import com.kheti.compose.KhetiTable
import com.kheti.compose.KhetiTheme
import com.kheti.compose.KhetiVerticalPoem
import com.kheti.compose.systemKhetiFontFamilies
import com.kheti.layout.KhetiInlineKind
import com.kheti.layout.KhetiInlineSpan
import com.kheti.layout.KhetiAlignment
import com.kheti.layout.KhetiRuby
import com.kheti.sample.resources.Res
import com.kheti.sample.resources.lxgw_neozhisong_screen
import com.kheti.sample.resources.lxgw_wenkai_regular

/**
 * 示例 App：把 kheti 的能力变成"看得见、可切换"的界面，用于肉眼验收。
 *
 * 可切换：字体风格（黑/宋/传统）、正文字号、赫蹏规则开关、基线网格、深色模式。
 * 含"原生 Compose Text vs kheti"的同文对照，用来直观看出中西文 ¼ 字宽间距与标点挤压。
 */
@Composable
fun App(
    initialVertical: Boolean = false,
    initialAnnotation: Boolean = false,
    initialGrid: Boolean = false,
) {
    var darkOverride by remember { mutableStateOf<Boolean?>(null) }
    var flavor by remember { mutableStateOf(KhetiFlavor.Classic) }
    var bodySize by remember { mutableStateOf(KhetiMetrics.Size.Normal) }
    var spacing by remember { mutableStateOf(true) }
    var grid by remember { mutableStateOf(initialGrid) }
    var vertical by remember { mutableStateOf(initialVertical) }
    var annotation by remember { mutableStateOf(initialAnnotation) }
    // 打包字体是 ~5.3k 字的屏幕子集（缺 蹏/〔〕/懈/殂 等），缺字会静默回退到系统字体，
    // 造成同一行字形风格不一致。故默认走**全量系统字体**，需要"跨端一致"时再切到打包字体。
    // 覆盖情况可用 `python3 tools/font-coverage/check.py` 核查。
    var bundledFont by remember { mutableStateOf(false) }

    val dark = darkOverride ?: isSystemInDarkTheme()
    val colors = if (dark) KhetiDarkColors else KhetiLightColors
    val bundled = rememberSampleFonts()
    val system = systemKhetiFontFamilies()
    val fonts = if (bundledFont) bundled else system
    val paper = if (dark) Color(0xFF14161A) else Color(0xFFF5F4ED)

    KhetiTheme(fonts = fonts, colors = colors, showGrid = grid) {
        MaterialTheme(colorScheme = if (dark) darkColorScheme() else lightColorScheme()) {
            Column(
                Modifier
                    .fillMaxSize()
                    .background(paper)
                    // enableEdgeToEdge 下内容会画到状态栏/导航栏底下，必须避开安全区
                    .windowInsetsPadding(WindowInsets.safeDrawing),
            ) {
                ControlBar(
                    flavor = flavor,
                    onFlavor = { flavor = it },
                    size = bodySize,
                    onSize = { bodySize = it },
                    spacing = spacing,
                    onSpacing = { spacing = it },
                    grid = grid,
                    onGrid = { grid = it },
                    vertical = vertical,
                    onVertical = { vertical = it },
                    annotation = annotation,
                    onAnnotation = { annotation = it },
                    bundledFont = bundledFont,
                    onBundledFont = { bundledFont = it },
                    dark = dark,
                    onDark = { darkOverride = it },
                    colors = colors,
                )
                Box(
                    Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()),
                ) {
                    Column(
                        Modifier
                            .align(Alignment.TopCenter)
                            .widthIn(max = 720.dp)
                            .padding(horizontal = 20.dp, vertical = 24.dp),
                    ) {
                        SectionLabel(
                            if (vertical) "诗 · 竖排（列序右→左，一句一列，标点悬挂到列尾之外）"
                            else "诗 · 七言绝句（居中、无缩进、行末标点悬挂）",
                            colors,
                        )
                        if (vertical) {
                            Box(
                                Modifier.fillMaxWidth().height(460.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                KhetiVerticalPoem(
                                    title = "赠汪伦",
                                    meta = "〔唐〕李白（701—762）",
                                    stanzas = Poems.赠汪伦,
                                    flavor = flavor,
                                )
                            }
                        } else {
                            KhetiPoem(
                                title = "赠汪伦",
                                meta = "〔唐〕李白（701—762）",
                                stanzas = Poems.赠汪伦,
                                flavor = flavor,
                            )
                        }

                        SectionLabel(
                            if (vertical) "词 · 竖排（上下阕）"
                            else "词 · 一剪梅（上下阕 = 两个诗节，段间距 24px）",
                            colors,
                        )
                        if (vertical) {
                            Box(
                                Modifier.fillMaxWidth().height(520.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                KhetiVerticalPoem(
                                    title = "一剪梅·红藕香残玉簟秋",
                                    meta = "〔宋〕李清照",
                                    stanzas = Poems.一剪梅,
                                    flavor = flavor,
                                )
                            }
                        } else {
                            KhetiPoem(
                                title = "一剪梅·红藕香残玉簟秋",
                                meta = "〔宋〕李清照",
                                stanzas = Poems.一剪梅,
                                flavor = flavor,
                            )
                        }

                        SectionLabel("古文 · 出师表（首行缩进 2em + 两端对齐 + 字间拉伸）", colors)
                        KhetiText(
                            text = Poems.出师表,
                            size = bodySize,
                            flavor = flavor,
                            alignment = KhetiAlignment.Justify,
                            firstLineIndentEm = 2f,
                            spacing = spacing,
                        )

                        SectionLabel("混排对照 · 左：原生 Compose Text ／ 右：kheti", colors)
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Column(Modifier.weight(1f)) {
                                PlainLabel("原生 Text", colors)
                                Text(
                                    text = Poems.混排对照,
                                    // 显式给色：MaterialTheme 只在 Surface 内部通过 LocalContentColor
                                    // 适配颜色；这里没有 Surface，原生 Text 会回退成黑色，
                                    // 深色模式下不可见。给同色后，本行对比才只反映排版差异。
                                    color = colors.ink,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontSize = 16.sp,
                                        lineHeight = 24.sp,
                                        fontFamily = fonts.song,
                                    ),
                                )
                            }
                            Column(Modifier.weight(1f)) {
                                PlainLabel("kheti", colors)
                                KhetiText(
                                    text = Poems.混排对照,
                                    size = KhetiMetrics.Size.Normal,
                                    flavor = flavor,
                                    spacing = spacing,
                                )
                            }
                        }

                        SectionLabel(
                            if (annotation) "行间注 · 块模式（上游 .heti--annotation：行高 2.25em、段间距 0、首行缩进 2em）"
                            else "行间注 · 内联模式（上游 .heti-ruby--inline：注音挤进行盒，不破坏 1.5 网格）",
                            colors,
                        )
                        KhetiText(
                            text = "赫蹏是专为中文网页内容设计的排版样式增强，可为读者带来更好的阅读体验。",
                            size = KhetiMetrics.Size.Normal,
                            flavor = flavor,
                            ruby = listOf(KhetiRuby(0, 1, "hè"), KhetiRuby(1, 2, "tí")),
                            annotationMode = annotation,
                        )

                        SectionLabel("文章模式 · 块级与行内元素（对应赫蹏演示页 D11–D14）", colors)
                        KhetiHeading("赫蹏与 kheti", KhetiMetrics.Heading.H2, flavor = flavor)
                        KhetiText(
                            text = "kheti 把赫蹏的排版规则搬到原生端；下面演示文章模式的各类元素。",
                            flavor = flavor,
                            spacing = spacing,
                            inline = listOf(
                                KhetiInlineSpan(0, 5, KhetiInlineKind.Code),
                                KhetiInlineSpan(14, 16, KhetiInlineKind.Mark),
                            ),
                        )
                        KhetiBlockquote(
                            text = "赫蹏不作为一个 CSS Reset 出现，而是根据通行的中文排版规范，" +
                                "对正文区域进行排版样式增强。",
                            flavor = flavor,
                        )
                        KhetiHr(dark = dark)
                        KhetiList(
                            items = listOf("贴合网格的排版", "中西文混排美化（自动 ¼ 字宽）", "全角标点挤压（½ / ¼ 字宽）"),
                            marker = if (flavor == KhetiFlavor.Classic) {
                                KhetiListMarker.HanIdeographic
                            } else {
                                KhetiListMarker.Decimal
                            },
                            flavor = flavor,
                        )
                        KhetiPre(text = "val kheti = Kheti()\nkheti.autoSpacing()   // 引擎内建，无需脚本", dark = dark)
                        KhetiTable(
                            header = listOf("项目", "上游", "kheti"),
                            rows = listOf(
                                listOf("字距", "0.02em", "引擎自绘"),
                                listOf("标点挤压", "−0.5em / −0.25em", "同一份增量"),
                                listOf("行间注", "<ruby>", "横排 + 竖排"),
                            ),
                            caption = "表 1：规则对照（表注在下，对应 caption-side: bottom）",
                            dark = dark,
                        )
                        KhetiFigure(caption = "图 1：多栏排版（--columns-2，栏内自动均衡）") {
                            KhetiColumns(text = Poems.出师表, count = 2, flavor = flavor, size = bodySize)
                        }
                        KhetiFootnotes(
                            notes = listOf(
                                "赫蹏：github.com/sivan/heti（MIT）",
                                "clreq：W3C《中文排版需求》",
                            ),
                            highlight = if (annotation) 1 else null,
                            onBack = { },
                            dark = dark,
                        )

                        Spacer(Modifier.height(48.dp))
                        Text(
                            text = "kheti · 赫蹏规则的 Kotlin/Compose Multiplatform 实现（上游 sivan/heti, MIT）",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp),
                            color = colors.ink,
                        )
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------ 控件

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ControlBar(
    flavor: KhetiFlavor,
    onFlavor: (KhetiFlavor) -> Unit,
    size: KhetiMetrics.Size,
    onSize: (KhetiMetrics.Size) -> Unit,
    spacing: Boolean,
    onSpacing: (Boolean) -> Unit,
    grid: Boolean,
    onGrid: (Boolean) -> Unit,
    vertical: Boolean,
    onVertical: (Boolean) -> Unit,
    annotation: Boolean,
    onAnnotation: (Boolean) -> Unit,
    bundledFont: Boolean,
    onBundledFont: (Boolean) -> Unit,
    dark: Boolean,
    onDark: (Boolean) -> Unit,
    colors: KhetiColors,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(if (dark) Color(0xFF1D2026) else Color(0xFFEDEBE1))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        // 每行都用 FlowRow：按钮多时自动换行，避免窄屏上互相挤压重叠
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            Tag("字体", colors)
            KhetiFlavor.entries.forEach { f ->
                Choice(
                    label = when (f) {
                        KhetiFlavor.Sans -> "黑体"
                        KhetiFlavor.Serif -> "宋体"
                        KhetiFlavor.Classic -> "传统"
                    },
                    selected = flavor == f,
                    onClick = { onFlavor(f) },
                )
            }
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            Tag("字号", colors)
            listOf(
                KhetiMetrics.Size.Small to "小",
                KhetiMetrics.Size.Normal to "标准",
                KhetiMetrics.Size.Large to "大",
                KhetiMetrics.Size.XLarge to "特大",
            ).forEach { (s, label) ->
                Choice(label = label, selected = size == s, onClick = { onSize(s) })
            }
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            Tag("排列", colors)
            // 横排/竖排做成显式按钮：此前只有开关，窄屏上被挤掉看不见
            Choice("横排", !vertical) { onVertical(false) }
            Choice("竖排", vertical) { onVertical(true) }
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            Tag("规则", colors)
            Choice("中西文间距", spacing) { onSpacing(!spacing) }
            Choice("基线网格", grid) { onGrid(!grid) }
            Choice("行间注(块)", annotation) { onAnnotation(!annotation) }
            Choice("打包字体", bundledFont) { onBundledFont(!bundledFont) }
            Choice("深色", dark) { onDark(!dark) }
        }
    }
}

@Composable
private fun Tag(text: String, colors: KhetiColors) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp),
        color = colors.ink,
    )
}

@Composable
private fun Choice(label: String, selected: Boolean, onClick: () -> Unit) {
    val padding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
    if (selected) {
        Button(onClick = onClick, contentPadding = padding) { Text(label, fontSize = 12.sp) }
    } else {
        OutlinedButton(onClick = onClick, contentPadding = padding) { Text(label, fontSize = 12.sp) }
    }
}

@Composable
private fun SectionLabel(text: String, colors: KhetiColors) {
    Spacer(Modifier.height(28.dp))
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp),
        color = colors.ink,
        modifier = Modifier.padding(bottom = 6.dp),
    )
}

@Composable
private fun PlainLabel(text: String, colors: KhetiColors) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
        color = colors.ink,
        modifier = Modifier.padding(bottom = 4.dp),
    )
}

// ------------------------------------------------------------------ 其它

/** 打包字体（SIL OFL）：让三端观感一致，也是 songci 等真实项目应采用的注入方式。 */
@OptIn(org.jetbrains.compose.resources.ExperimentalResourceApi::class)
@Composable
private fun rememberSampleFonts(): KhetiFontFamilies {
    val song = FontFamily(org.jetbrains.compose.resources.Font(Res.font.lxgw_neozhisong_screen))
    val kai = FontFamily(org.jetbrains.compose.resources.Font(Res.font.lxgw_wenkai_regular))
    return remember(song, kai) {
        KhetiFontFamilies(song = song, kai = kai, hei = FontFamily.SansSerif)
    }
}

/** 未使用但保留：便于后续接入着重号演示。 */
internal val emphasisDemoWeight = FontWeight.Normal
