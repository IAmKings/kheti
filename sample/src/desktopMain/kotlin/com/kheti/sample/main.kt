package com.kheti.sample

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "kheti · 中文诗词排版（赫蹏规则实现）",
        state = rememberWindowState(width = 880.dp, height = 940.dp),
    ) {
        App()
    }
}
