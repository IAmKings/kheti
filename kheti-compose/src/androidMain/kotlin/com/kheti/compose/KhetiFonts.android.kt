package com.kheti.compose

import androidx.compose.ui.text.font.FontFamily

/**
 * Android 系统字体近似。
 *
 * ⚠️ **Android 没有楷体**：`kai` 退化为 `FontFamily.Serif`（Noto Serif CJK，本质是宋体）。
 * 这正是 `songci` 打包霞鹜文楷的原因——需要真楷体时请注入打包字体。
 * `song` 的 `FontFamily.Serif` 在 Android 上解析为 Noto Serif CJK，观感接近宋体。
 */
actual fun systemKhetiFontFamilies(): KhetiFontFamilies = KhetiFontFamilies(
    song = FontFamily.Serif,
    kai = FontFamily.Serif,
    hei = FontFamily.SansSerif,
)
