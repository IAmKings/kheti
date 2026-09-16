package com.kheti.compose

import androidx.compose.ui.text.font.FontFamily

/**
 * 桌面（JVM）系统字体近似。
 *
 * 由 Skiko 的字体管理器决定具体映射（macOS 下 Serif → Songti/Times 系，Windows 下 → SimSun 系）。
 * 校验与一致性渲染请注入打包字体（见 `reference/fonts/`）。
 */
actual fun systemKhetiFontFamilies(): KhetiFontFamilies = KhetiFontFamilies(
    song = FontFamily.Serif,
    kai = FontFamily.Serif,
    hei = FontFamily.SansSerif,
)
