package com.kheti.compose

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.kheti.KhetiMetrics
import com.kheti.layout.KhetiTextStyle

/**
 * 赫蹏的三种字体风格（上游 `heti--sans` / `heti--serif` / `heti--classic`）。
 *
 * 上游文档：黑体、宋体、"传统"（正文宋体 + 标题楷体）。
 */
enum class KhetiFlavor {
    /** 黑体：正文与标题均用黑体。 */
    Sans,

    /** 宋体：正文与标题均用宋体。 */
    Serif,

    /** 传统（赫蹏默认）：正文宋体 + 标题楷体 + 标题 800 字重居中。 */
    Classic,
}

/**
 * 三种角色的字体族。
 *
 * ⚠️ **平台现实**（Phase 0 调研）：Android 无楷体、iOS 无思源宋、桌面各异。
 * 因此 [systemKhetiFontFamilies] 只能给出**近似**；要得到真正的宋/楷观感与跨平台一致，
 * 必须注入打包字体（如霞鹜新致宋 / 霞鹜文楷，OFL）：
 * ```
 * val fonts = KhetiFontFamilies(
 *     song = FontFamily(Font(Res.font.lxgw_neozhisong)),
 *     kai = FontFamily(Font(Res.font.lxgw_wenkai)),
 *     hei = FontFamily.Default,
 * )
 * ```
 */
@Immutable
data class KhetiFontFamilies(
    val song: FontFamily,
    val kai: FontFamily,
    val hei: FontFamily,
)

/** 各平台的系统字体近似（详见 [KhetiFontFamilies] 的说明）。 */
expect fun systemKhetiFontFamilies(): KhetiFontFamilies

/** 正文/标题颜色（不设背景：排版库不应决定纸张颜色）。 */
@Immutable
data class KhetiColors(
    val ink: Color,
    val inkSecondary: Color,
)

val KhetiLightColors = KhetiColors(
    ink = Color(0xFF1A1A1A),
    inkSecondary = Color(0xFF6B6B6B),
)

val KhetiDarkColors = KhetiColors(
    ink = Color(0xFFE8E6E0),
    inkSecondary = Color(0xFF9A9A9A),
)

val LocalKhetiFontFamilies = staticCompositionLocalOf { systemKhetiFontFamilies() }
val LocalKhetiColors = staticCompositionLocalOf { KhetiLightColors }

/**
 * 是否绘制**排版网格**（校验"贴合网格"用）。
 *
 * 网格由布局自身的几何推导，因此永远对齐：
 * - 横排：按每个**行盒**顶边画横线；
 * - 竖排：按每个**列盒**边界画竖线（竖排的栅格是纵向的）。
 */
val LocalKhetiShowGrid = staticCompositionLocalOf { false }

/**
 * 主题：提供字体族与颜色。默认跟随系统深色模式（对应上游 `prefers-color-scheme` 自适应）。
 */
@Composable
fun KhetiTheme(
    fonts: KhetiFontFamilies = systemKhetiFontFamilies(),
    colors: KhetiColors = if (isSystemInDarkTheme()) KhetiDarkColors else KhetiLightColors,
    /** 绘制排版网格（横排画行盒横线、竖排画列盒竖线）。 */
    showGrid: Boolean = false,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalKhetiFontFamilies provides fonts,
        LocalKhetiColors provides colors,
        LocalKhetiShowGrid provides showGrid,
    ) {
        content()
    }
}

// ---------------------------------------------------------------- 样式派生

/** 正文字体族：黑体风格用黑体，其余用宋体（对应上游 `--sans` / `--serif` / `--classic`）。 */
fun bodyFamily(flavor: KhetiFlavor, fonts: KhetiFontFamilies): FontFamily =
    if (flavor == KhetiFlavor.Sans) fonts.hei else fonts.song

/** 标题字体族：传统风格用楷体，其余与正文一致（上游 `--classic` 的核心差异）。 */
fun headingFamily(flavor: KhetiFlavor, fonts: KhetiFontFamilies): FontFamily =
    if (flavor == KhetiFlavor.Classic) fonts.kai else bodyFamily(flavor, fonts)

fun KhetiTextStyle.Companion.body(
    size: KhetiMetrics.Size,
    flavor: KhetiFlavor,
    fonts: KhetiFontFamilies,
): KhetiTextStyle = of(size, bodyFamily(flavor, fonts))

fun KhetiTextStyle.Companion.heading(
    level: KhetiMetrics.Heading,
    flavor: KhetiFlavor,
    fonts: KhetiFontFamilies,
): KhetiTextStyle = heading(level, headingFamily(flavor, fonts), FontWeight(800))
