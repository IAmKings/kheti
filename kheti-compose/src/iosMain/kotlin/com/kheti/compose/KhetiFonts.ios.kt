package com.kheti.compose

import androidx.compose.ui.text.font.FontFamily

/**
 * iOS 系统字体近似。
 *
 * iOS 的 CJK 回退字体为 PingFang SC（黑体），`FontFamily.Serif` 的 CJK 回退会走到
 * Songti SC（宋体）；**楷体（Kaiti SC）无法通过通用族名拿到**，故 `kai` 暂用 Serif。
 * 需要真楷体/跨端一致时请注入打包字体（霞鹜文楷，OFL）。
 */
actual fun systemKhetiFontFamilies(): KhetiFontFamilies = KhetiFontFamilies(
    song = FontFamily.Serif,
    kai = FontFamily.Serif,
    hei = FontFamily.SansSerif,
)
