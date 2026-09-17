# kheti

「赫蹏」（[heti](https://github.com/sivan/heti)）中文排版规则的 **Kotlin / Compose Multiplatform** 移植，
面向中文诗词与古籍版式（横排 + 竖排），并已对齐上游演示页的全部排版能力。

> 命名：**K**otlin + **heti** = kheti。
> 上游为 MIT（Copyright © 2020 Sivan），版权声明见 [`NOTICE`](NOTICE)。

## 现状

**84 项桌面测试 + 4 项真机（Android 16）测试全绿**；上游演示页能力 **D1–D15 全部对齐**
（逐项对账见 **[docs/PRD.md](docs/PRD.md)**「§2.1b 上游演示站功能对账」）。

| 结论 | 证据 |
|---|---|
| 赫蹏的效果只有两类原语：**单字 advance 增量** + **区块几何**，没有 Compose 无法表达的特性 | 源码逐条提取，`docs/decisions.md` |
| 不能用 `Text` + `SpanStyle.letterSpacing` 冒充：**同一份样式在 Skia 与 Minikin 上几何不同** | Spike A 实测：Skia 尾增量 = X，Android = X/2（`docs/phase0-evidence.md`） |
| 自研「度量 + 定位 + 分段自绘」引擎，**两端逐像素一致** | Spike B：整行 vs 分段绘制差异 **0**；`−0.5em` 挤压 = **−8px** |
| 规则层与上游**逐字符等价** | 21 条夹具的差分金标准（Node 逐字复制上游正则） |
| **渲染级对拍**：与真实 Chrome 中的 `heti@0.9.6` 逐字几何比对 | 纯 CJK 最大偏差 **0.03px**（`reference/browser-geometry.tsv`） |
| 竖排（Compose 全平台零支持） | 6 列、列距 = 行高 30 逻辑px、縦中横、区域标点、悬挂、ruby 在基文右侧 |
| 真机与桌面规则等价（与字体无关的量） | `KhetiEngineAndroidTest`（真机 PJZ110 / Android 16） |
| 已发布到 Maven Central，第三方可一行依赖 | `io.github.iamkings:kheti-compose:0.1.0`（Android AAR / Desktop JAR / iOS klib，全构件带 GPG 签名） |

## 引用

已发布到 **Maven Central**：[io.github.iamkings](https://central.sonatype.com/search?q=io.github.iamkings) ·
[浏览全部构件](https://repo1.maven.org/maven2/io/github/iamkings/)。一行依赖即可，无需源码引入：

```kotlin
commonMain.dependencies {
    implementation("io.github.iamkings:kheti-compose:0.1.0")
}
```

Gradle 会按目标平台自动选构件：Android 取 AAR、Desktop/JVM 取 JAR、iOS 取 klib。
`kheti-compose` 已把 `kheti-core`、`kheti-layout`、`compose.ui`、`compose.runtime`
声明为 `api`，使用方只需声明这一个依赖即可编译。
只需规则层或引擎层时，可单独依赖 `kheti-core` / `kheti-layout`。

> ⚠️ 0.1.0 已永久占用（Maven Central 不可覆盖）。发布新版本先递增
> `gradle.properties` 的 `kheti.version`，再走
> **[docs/publishing.md](docs/publishing.md)** 的发布流程
> （凭据配置有现成脚本 `tools/setup-central-publishing.sh`；上传后必需的一次
> Portal 调用、动态出口 IP 的绕行方法、镜像同步延迟的说明都在里面）。

## 快速开始

```kotlin
KhetiTheme {                        // 默认跟随系统深色模式
    KhetiPoem(                      // 诗词：居中、无缩进、楷体标题、行末标点悬挂
        title = "赠汪伦",
        meta = "〔唐〕李白",
        stanzas = listOf(
            listOf("李白乘舟将欲行，", "忽闻岸上踏歌声。", "桃花潭水深千尺，", "不及汪伦送我情。"),
        ),
    )

    KhetiText(                      // 散文/古文
        text = "先帝创业未半而中道崩殂……",
        flavor = KhetiFlavor.Classic,   // 正文宋体 + 标题楷体
        alignment = KhetiAlignment.Justify,
        firstLineIndentEm = 2f,         // 古文首行缩进
        ruby = listOf(KhetiRuby(0, 1, "hè")),   // 行间注（下标基于原文）
    )

    KhetiVerticalPoem(              // 竖排：块序右→左，各块保留字号/字重层级
        title = "赠汪伦", meta = "〔唐〕李白", stanzas = listOf(listOf("李白乘舟将欲行，")),
    )
}
```

### 文章模式（对应上游演示页 D11–D15）

```kotlin
KhetiHeading("标题", KhetiMetrics.Heading.H2)          // 600 字重；h1–h3 letter-spacing .05em
KhetiBlockquote("引用")                                 // 底色 + 2em/1em 缩进内边距
KhetiHr()                                              // 宽 30% / 高 1px / 上下 2 网格
KhetiPre("val x = 1")                                  // 等宽 + 背景（**不做**赫蹏间距，同上游跳过 pre/code）
KhetiList(items, KhetiListMarker.HanIdeographic)       // • ／ 1. ／ 一、／ A. ／ iv.
KhetiTable(header, rows, caption = "表 1")             // 1px 边框、单元格 6/8、表注在下
KhetiFigure(caption = "图 1") { KhetiColumns(text, count = 2) }   // 多栏：--columns-N / --columns-Nem
KhetiFootnotes(notes, highlight = 1, onBack = { })     // .heti-fn：59px + 1px 分隔线 + 高亮

KhetiText(
    text = "参见《赫蹏》与 clreq 规范",
    inline = listOf(
        KhetiInlineSpan(3, 6, KhetiInlineKind.Proper),  // u 专名号
        KhetiInlineSpan(9, 14, KhetiInlineKind.Code),   // 等宽 + 底色
    ),
    lang = KhetiLang.Latin,   // 西文容器字距归零（上游 [lang=en-US]）
)
```

要获得真正的宋/楷观感与跨端一致，注入打包字体（OFL）：

```kotlin
KhetiTheme(fonts = KhetiFontFamilies(
    song = FontFamily(Font(Res.font.lxgw_neozhisong_screen)),   // 霞鹜新致宋
    kai  = FontFamily(Font(Res.font.lxgw_wenkai_regular)),      // 霞鹜文楷
    hei  = FontFamily.Default,
)) { /* … */ }
```

> ⚠️ 打包字体是 **CJK 子集**（ASCII 仅 5/95 码位），缺字会静默回退导致同行字形不一致。
> 用 `python3 tools/font-coverage/check.py` 核查；示例 App 默认走系统字体。

## 模块

```
kheti-core      纯 Kotlin 规则层（零 Compose 依赖，100% 可单测）
                增量规则 / 度量常量 / 引号策略 / 竖排字形表 / q 引号包装
kheti-layout    度量·定位·自绘引擎（TextMeasurer + Canvas）
                横排 / 竖排 / 行间注 / 行内样式 / 多栏 / 网格推导
kheti-compose   公共 API：KhetiTheme · KhetiText · KhetiPoem · KhetiVerticalText ·
                KhetiVerticalPoem · KhetiColumns · KhetiHeading · KhetiBlockquote ·
                KhetiHr · KhetiPre · KhetiList · KhetiTable · KhetiFigure · KhetiFootnotes
sample          Android + Desktop 示例 App（同一份 App() 组合）
tools/          对拍与守门工具（金标准、浏览器几何、字体覆盖）
reference/      金标准数据、对拍字体、渲染与真机证据
docs/           PRD（需求与验收基线）· ADR · 平台实测证据 · 保真度账本
```

## 示例 App

```bash
./gradlew :sample:run          # 桌面窗口
./gradlew :sample:installDebug # 安装到已连接设备
```

可切换：**字体风格**（黑体/宋体/传统）· **正文字号** · **排列**（横排/竖排）· **赫蹏规则开关** ·
**基线网格** · **行间注（内联/块）** · **打包字体** · **深色模式**。

内容含：诗（赠汪伦）· 词（一剪梅·上下阕）· 古文（出师表，首行缩进 + 两端对齐）·
中西文混排对照（左原生 `Text` / 右 kheti）· 行间注 · **文章模式**（标题/引用/分隔线/代码/列表/
表格/多栏/脚注）。

真机证据：`reference/app-android.png`、`app-android-poem.png`（字列对齐 + 悬挂）、
`app-android-vertical.png`（竖排 6 列、列距 120px = 30 逻辑px）。

## 验收与工具

```bash
# 测试
./gradlew :kheti-core:desktopTest :kheti-layout:desktopTest :kheti-compose:desktopTest
./gradlew :kheti-layout:connectedDebugAndroidTest   # 真机（Minikin）不变量

# 对拍尺子
node tools/reference-gen/heti-golden.mjs      > reference/heti-golden.tsv       # 规则级金标准
node tools/reference-gen/browser-geometry.mjs > reference/browser-geometry.tsv  # 真实 Chrome + heti@0.9.6
python3 tools/font-coverage/check.py                                            # 字体覆盖守门

# 防静默退化的守卫测试
NoPlatformLetterSpacingGuardTest   # 渲染路径禁止平台 letterSpacing（ADR-001）
KhetiColorResolutionGuardTest      # 颜色必须显式解析；inkSecondary 仅限诗词元信息
```

需求与验收基线 **[docs/PRD.md](docs/PRD.md)** · 架构决策 `docs/decisions.md` ·
平台实测 `docs/phase0-evidence.md` · 逐特性保真度 `docs/fidelity.md`。
AI 协作约定见 `.trellis/spec/guides/ui-text-and-color-conventions.md`。

## 已知缺口

| 项 | 说明 |
|---|---|
| iOS 验收 | `iosArm64`/`iosSimulatorArm64` target 已配置但**未实测**（唯一剩余里程碑） |
| 西文跨端几何一致 | 需先提供**中西文全覆盖**的打包字体（当前参考字体是 CJK 子集） |
| `hyphens: auto` / BiDi `isolate` | 平台字典与脚本差异，接受偏差 |
| 可编辑（IME） | 设计上不做：展示型，与 Android 官方竖排同定位 |
| 颜色入口去重 | `resolveKhetiColor` 目前仅 `KhetiColumns` 采用；`KhetiText`/`KhetiVerticalText` 保留等价内联写法（行为已被守卫覆盖） |

## 工具链

Kotlin 2.4.10 · Compose Multiplatform 1.11.1 · AGP 9.3.1 · Gradle 9.7.0 ·
目标：Android / iOS / Desktop(JVM)。依赖走阿里云镜像（本机 `dl.google.com` 不可达）。

## 许可

移植部分遵循上游 MIT 许可，版权声明见 [`NOTICE`](NOTICE)。
