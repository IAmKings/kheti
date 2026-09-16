package com.kheti

/**
 * 赫蹏字符类与标点集合（逐字对应上游 `js/heti-addon.js` 的常量）。
 *
 * 上游：https://github.com/sivan/heti —— MIT License, Copyright (c) 2020 Sivan。
 * 这些区间是**规范**，不是可调参数：改动即偏离赫蹏。
 */
internal object HetiChars {
    /** CJK 统一表意文字及各扩展区（与上游 `CJK` 完全一致）。 */
    const val CJK: String =
        "\u2e80-\u2eff\u2f00-\u2fdf\u3040-\u309f\u30a0-\u30fa\u30fc-\u30ff" +
            "\u3100-\u312f\u3200-\u32ff\u3400-\u4dbf\u4e00-\u9fff\uf900-\ufaff"

    /** 拉丁与希腊字母。 */
    const val A: String = "A-Za-z\u0080-\u00ff\u0370-\u03ff"

    /** 阿拉伯数字。 */
    const val N: String = "0-9"

    /** ASCII 符号（与上游 `S` 等价，仅按 Kotlin 字符串转义规则书写）。 */
    const val S: String = "`~!@#\$%^&*()\\-_=+\\[\\]{}|\\\\;:'\",<.>/?"

    /** 西文与数字、符号的并集（上游 `ANS`）。 */
    const val ANS: String = "$A$N$S"

    // ---- 标点分类（上游 REG_BD_*）----
    /** 停顿号（句读）。 */
    const val BD_STOP: String = "。．，、：；！‼？⁇"

    /** 间隔符。 */
    const val BD_SEP: String = "·・‧"

    /** 全角开括号 / 开引号。 */
    const val BD_OPEN: String = "「『（《〈【〖〔［｛"

    /** 全角闭括号 / 闭引号。 */
    const val BD_CLOSE: String = "」』）》〉】〗〕］｝"

    /** 停顿号 + 开括号 + 闭括号（挤压触发集合）。 */
    const val BD_START: String = "$BD_OPEN$BD_CLOSE"

    /** 闭括号类之后可挤压的集合。 */
    const val BD_END: String = "$BD_STOP$BD_OPEN$BD_CLOSE"

    /** 弯引号：开。 */
    const val BD_HALF_OPEN: String = "“‘"

    /** 弯引号：闭。 */
    const val BD_HALF_CLOSE: String = "”’"

    /** 弯引号全集。 */
    const val BD_HALF_START: String = "$BD_HALF_OPEN$BD_HALF_CLOSE"
}
