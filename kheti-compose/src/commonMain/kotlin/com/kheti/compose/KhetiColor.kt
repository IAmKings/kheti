package com.kheti.compose

import androidx.compose.ui.graphics.Color

/**
 * 解析组件文字色（**唯一入口**）。
 *
 * 为什么必须集中：绘制层遇到 `Color.Unspecified` 会回退到**平台默认色（黑）**，
 * 于是深色模式下变成黑字压深底 —— 整片内容不可见。真机上已经踩过两次
 * （`KhetiText` 与示例页的原生 `Text`），因此这里把规则固化成一处：
 *
 * - 调用方显式给色 → 用它（例如链接色）
 * - 未给（`Unspecified`）→ 用主题正文色 [KhetiColors.ink]
 *
 * 相关守卫见 `KhetiColorResolutionGuardTest`。
 */
fun resolveKhetiColor(color: Color, colors: KhetiColors): Color =
    if (color == Color.Unspecified) colors.ink else color
