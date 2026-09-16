package com.kheti

/**
 * 赫蹏版式常量——**唯一事实来源**。
 *
 * 逐条来自上游 `lib/_variables.scss`、`lib/_base.scss` 以及 `lib/modifiers/` 下的各 modifier。
 * 移植时不允许"凭感觉调"：任何偏离都应先在 `docs/decisions.md` 记录理由。
 *
 * 本文件刻意不依赖 Compose（`Sp`/`Dp` 在 kheti-compose 层换算），以保证规则层可 100% 单测。
 */
object KhetiMetrics {
    /** 标准字号 16px（上游 `$font-size-normal`）。 */
    const val FONT_SIZE_NORMAL: Float = 16f

    /** 标准行高倍数 1.5 → 垂直网格单位 24px。 */
    const val LINE_HEIGHT: Float = 1.5f

    /** 垂直网格单位 = 字号 × 行高。 */
    const val GRID_UNIT: Float = FONT_SIZE_NORMAL * LINE_HEIGHT // 24

    /** 行宽上限 42em（中文 CPL 建议 30~50）。 */
    const val LINE_LENGTH_EM: Float = 42f

    /** CJK 字距 0.02em（非 CJK 为 0，见 [letterSpacingEm]）。 */
    const val LETTER_SPACING_CJK_EM: Float = 0.02f

    /** 竖排字距 0.125em（上游 `--vertical`）。 */
    const val LETTER_SPACING_VERTICAL_EM: Float = 0.125f

    /** 古文首行缩进 2em。 */
    const val TEXT_INDENT_EM: Float = 2f

    /**
     * 行间注版式的行高倍数（上游 `.heti--annotation { p { line-height: $line-height-expanded-ultra } }`）。
     * 注音占行盒上方，故需 2.25 倍行高；该模式下段间距归零，间距由行高提供。
     */
    const val LINE_HEIGHT_ANNOTATION: Float = 2.25f

    /** 注音相对基文的字号比例（浏览器 `<rt>` 默认约 50%）。 */
    const val RUBY_SCALE: Float = 0.5f

    /** 段落上边距 = 0.5 网格。 */
    const val PARAGRAPH_MARGIN_TOP: Float = GRID_UNIT * 0.5f // 12

    /** 段落下边距 = 1 网格（保证块级元素贴合垂直栅格）。 */
    const val PARAGRAPH_MARGIN_BOTTOM: Float = GRID_UNIT // 24

    /** 字重（上游 `$font-weight-*`）。 */
    const val WEIGHT_LIGHTER: Int = 200
    const val WEIGHT_NORMAL: Int = 400
    const val WEIGHT_BOLD: Int = 600
    const val WEIGHT_BOLDER: Int = 800

    /** 中西文间距 ¼ em。 */
    const val GAP_EM: Float = AdjustmentPlanner.GAP_EM

    /** 标点挤压 ½ em。 */
    const val COMPRESS_HALF_EM: Float = AdjustmentPlanner.COMPRESS_HALF_EM

    /** 标点挤压 ¼ em。 */
    const val COMPRESS_QUARTER_EM: Float = AdjustmentPlanner.COMPRESS_QUARTER_EM

    /**
     * 标题字号档（上游 `_variables.scss` 的 `$font-size-h*` / `$line-height-size-h*`）。
     * 赫蹏传统风格下标题用**楷体 + 800 字重 + 居中**。
     */
    enum class Heading(val fontSize: Float, val lineHeightPx: Float) {
        H1(32f, 48f),
        H2(24f, 36f),
        H3(20f, 36f),
        H4(18f, 24f),
        H5(16f, 24f),
        H6(14f, 24f),
    }

    /**
     * 赫蹏字号档（上游 `heti-x-large` 等 helper class）。
     *
     * [letterSpacingEm] 是**容器继承后的有效值**：上游 `.heti { letter-spacing: .02em }`，
     * `-x-large` 另用 `.heti .heti-x-large { letter-spacing: .05em }` 覆盖为 0.05em。
     *
     * 注意：`.heti heti-spacing { letter-spacing: normal }` 是**匹配该元素本身**的规则，
     * 匹配规则优先于继承，所以 gap 区间始终为 0——x-large 也不例外。该判断在引擎中处理。
     */
    enum class Size(val fontSize: Float, val lineHeightPx: Float, val letterSpacingEm: Float) {
        XSmall(12f, 18f, LETTER_SPACING_CJK_EM),
        Small(14f, GRID_UNIT, LETTER_SPACING_CJK_EM),
        Normal(16f, GRID_UNIT, LETTER_SPACING_CJK_EM),
        Large(18f, GRID_UNIT, LETTER_SPACING_CJK_EM),
        XLarge(20f, 30f, 0.05f),
    }

    /**
     * 可悬挂标点（行末悬挂 / 上游 `heti-hang` 的自动判定集合）。
     *
     * 赫蹏官方示例是对行末标点手动套 `<span class="heti-hang">`；本项目把它自动化，
     * 属于**有意超集**（散文模式默认关闭以保持规则等价）。
     */
    const val HANGABLE: String = "。．，、：；！‼？⁇…·・‧」』）》〉】〗〕］｝”’"

    fun isHangable(ch: Char): Boolean = ch in HANGABLE

    /** 行首禁则（简化版 clreq：这些字符不应出现在行首）。 */
    const val NO_LINE_START: String = "。．，、：；！‼？⁇」』）》〉】〗〕］｝”’·・‧"

    fun isNoLineStart(ch: Char): Boolean = ch in NO_LINE_START

    fun isCjk(ch: Char): Boolean = ch.code in 0x2E80..0x2EFF ||
        ch.code in 0x2F00..0x2FDF || ch.code in 0x3040..0x309F ||
        ch.code in 0x30A0..0x30FA || ch.code in 0x30FC..0x30FF ||
        ch.code in 0x3100..0x312F || ch.code in 0x3200..0x32FF ||
        ch.code in 0x3400..0x4DBF || ch.code in 0x4E00..0x9FFF ||
        ch.code in 0xF900..0xFAFF
}

/**
 * 中文引号规范预设（上游 `$chinese-quote-presets`）。
 *
 * `cn`：GB/T 15834-2011，简体中文国家标准的弯引号，竖排改用直角引号；
 * `tw`：台湾《重訂標點符號手冊》；
 * `common`：赫蹏默认值（部分中文社区在简体中亦采用台湾规范）。
 */
data class QuotePolicy(
    val horizontalPrimary: String,
    val horizontalSecondary: String,
    val verticalPrimary: String,
    val verticalSecondary: String,
    /** 次引号（嵌套第 2 层）。为空则回退到主引号。 */
    val horizontalOpenSecondary: String = "",
    val horizontalCloseSecondary: String = "",
    val verticalOpenSecondary: String = "",
    val verticalCloseSecondary: String = "",
) {
    companion object {
        // 上游 $chinese-quote-presets 每项为 4 个值：主开/主闭/次开/次闭
        /** GB/T 15834-2011：横排弯引号，竖排直角引号。 */
        val CN = QuotePolicy(
            "“", "”", "『", "』",
            horizontalOpenSecondary = "‘", horizontalCloseSecondary = "’",
            verticalOpenSecondary = "「", verticalCloseSecondary = "」",
        )

        /** 台湾《重訂標點符號手冊》：横竖皆直角引号。 */
        val TW = QuotePolicy(
            "「", "」", "「", "」",
            horizontalOpenSecondary = "『", horizontalCloseSecondary = "』",
            verticalOpenSecondary = "『", verticalCloseSecondary = "』",
        )

        /** 赫蹏默认 `$chinese-quote-set: "common"`（台湾规范）。 */
        val COMMON = TW

        /** 赫蹏默认。 */
        val DEFAULT = COMMON
    }
}

/** 书写方向。 */
enum class KhetiFlow { Horizontal, Vertical }

/** 标点定位规范（竖排时影响标点在字身框内的位置）。 */
enum class KhetiRegion {
    /** 中国大陆：标点靠字身框起始侧（横排左下 / 竖排右上）。 */
    CN,

    /** 台湾/香港：标点居中。 */
    TW,
}
