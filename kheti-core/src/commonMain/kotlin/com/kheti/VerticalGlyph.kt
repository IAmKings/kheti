package com.kheti

/** 竖排时单个字形的朝向策略（对应 CSS `text-orientation: mixed | upright` 与縦中横）。 */
enum class VerticalOrientation {
    /** 正立（汉字、假名、句读标点）。 */
    Upright,

    /** 顺时针旋转 90°（拉丁词、括号、破折号、省略号）。 */
    Sideways,

    /** 縦中横：2~3 位数字横排于一个字身框内。 */
    CombineUpright,
}

/**
 * 竖排字形表：**不依赖字体**的排布规则。
 *
 * 关键事实（见 `docs/phase0-evidence.md`）：Compose 全平台没有任何竖排 API，
 * SkParagraph 也没有；`vert`/`vrt2` 在横排文本里是 no-op。因此竖排的字形朝向与
 * 标点定位只能由我们自己决定——这就是本表存在的原因。
 *
 * ⚠️ 标点偏移是**基于通用字形设计**的近似（认为横排 `。` 的墨迹位于字身框左下、
 * 竖排规范要求右上）。个别字体若把句读画在别处，需要按字体微调——记录为已知近似。
 */
object VerticalGlyph {

    /** 竖排需要旋转 90° 的字符：各类括号、破折号、省略号、斜杠。 */
    const val ROTATED: String = "（）〈〉《》【】〖〗〔〕［］｛｝「」『』—…～－／＼"

    /** 竖排中需要按区域规范挪到字身框角位的句读标点。 */
    const val CORNER_PUNCT: String = "。．，、：；！‼？⁇"

    /** 横向本就居中的标点（间隔符等），竖排保持居中。 */
    const val CENTERED_PUNCT: String = "·・‧‥"

    fun isRotated(ch: Char): Boolean = ch in ROTATED

    fun isCornerPunct(ch: Char): Boolean = ch in CORNER_PUNCT

    fun isCenteredPunct(ch: Char): Boolean = ch in CENTERED_PUNCT

    /** 是否为需要按标点规则定位的字符。 */
    fun isPunct(ch: Char): Boolean = isCornerPunct(ch) || isCenteredPunct(ch) || ch in "“”‘’"

    /**
     * 竖排时把**横排字形**挪到正确位置所需的偏移（单位 em，`dx` 向右、`dy` 向下）。
     *
     * - 中国大陆（[KhetiRegion.CN]）：标点靠字身框起始侧，竖排即**右上角** →
     *   横排墨迹在左下，故平移 (+½, −½) em。
     * - 台湾/香港（[KhetiRegion.TW]）：标点居中 → 平移 (+¼, −¼) em。
     * - 本就居中的标点与引号：不偏移。
     */
    fun punctuationOffsetEm(ch: Char, region: KhetiRegion): Pair<Float, Float> = when {
        isCornerPunct(ch) -> if (region == KhetiRegion.CN) 0.5f to -0.5f else 0.25f to -0.25f
        else -> 0f to 0f
    }

    /** 单字符的默认朝向（拉丁/数字的最终朝向由引擎按整段长度决定）。 */
    fun defaultOrientation(ch: Char): VerticalOrientation = when {
        isRotated(ch) -> VerticalOrientation.Sideways
        KhetiMetrics.isCjk(ch) -> VerticalOrientation.Upright
        ch.isDigit() -> VerticalOrientation.CombineUpright
        ch in 'A'..'Z' || ch in 'a'..'z' -> VerticalOrientation.Upright
        else -> VerticalOrientation.Upright
    }
}

/**
 * 数字段的竖排朝向。
 *
 * **縦中横只适用于 2 位数**：半角数字宽约 ½ em，2 位恰好占满一个字身框。
 * 3 位及以上若仍作縦中横，会宽出 ½ em，压住后面的字（实测表现为"年份过大、
 * 且紧跟的 `）` 被盖住"）——因此 3 位以上一律旋转。
 */
fun digitRunOrientation(length: Int): VerticalOrientation = when {
    length <= 1 -> VerticalOrientation.Upright
    length == 2 -> VerticalOrientation.CombineUpright
    else -> VerticalOrientation.Sideways
}

/** 单个拉丁字母正立，多字母成词则旋转。 */
fun latinRunOrientation(length: Int): VerticalOrientation =
    if (length <= 1) VerticalOrientation.Upright else VerticalOrientation.Sideways
