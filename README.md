# kheti

「赫蹏」（[heti](https://github.com/sivan/heti)）中文排版规则的 **Kotlin / Compose Multiplatform** 移植，
面向中文诗词与古籍版式（横排 + 竖排）。

> 命名：**K**otlin + **heti** = kheti。

## 现状：Phase 0 + Phase 2 完成（规则层 / 引擎 / 诗词 API 全部可用）

| 结论 | 证据 |
|---|---|
| 赫蹏的效果只有两类原语：**单字 advance 增量** + **区块几何**，没有 Compose 无法表达的特性 | 源码逐条提取，见 `docs/decisions.md` |
| 不能用 `Text` + `SpanStyle.letterSpacing` 冒充：**同一份样式在 Skia 与 Minikin 上几何不同** | [Spike A 实测](docs/phase0-evidence.md)：Skia 尾增量 = X，Android = X/2 |
| 自研「度量 + 定位 + 分段自绘」引擎可行，且**两端逐像素一致** | Spike B：整行 vs 分段绘制像素差异 **0**；`−0.5em` 挤压 = **−8px**（两端相同） |
| 规则层与上游**逐字符等价** | 21 条夹具的 Node 差分金标准，Kotlin 端全部通过 |
| **诗词版式已可渲染**：居中、无缩进、行末标点自动悬挂（字列对齐、标点挂在列外）、1.5 网格、两端对齐 | `reference/poem-horizontal.png` + `reference/poem-kheti-api.png` + 像素级结构断言 |
| **真机与桌面规则等价**：em 增量、行盒网格、悬挂判定在两端断言一致（与字体无关的量） | `KhetiEngineAndroidTest`（真机 PJZ110 / Android 16） |

逐特性达成度见 **[docs/fidelity.md](docs/fidelity.md)**。

## 用法

```kotlin
KhetiTheme {                       // 默认跟随系统深色模式
    KhetiPoem(
        title = "赠汪伦",
        meta = "〔唐〕李白",
        stanzas = listOf(
            listOf("李白乘舟将欲行，", "忽闻岸上踏歌声。", "桃花潭水深千尺，", "不及汪伦送我情。"),
        ),
    )
}

KhetiText(                          // 散文/古文
    text = "先帝创业未半而中道崩殂……",
    size = KhetiMetrics.Size.Normal,
    flavor = KhetiFlavor.Classic,   // 正文宋体 + 标题楷体
    alignment = KhetiAlignment.Justify,
    firstLineIndentEm = 2f,         // 古文首行缩进
)
```

要获得真正的宋/楷观感与跨平台一致，注入打包字体（OFL）：

```kotlin
KhetiTheme(fonts = KhetiFontFamilies(
    song = FontFamily(Font(Res.font.lxgw_neozhisong)),
    kai  = FontFamily(Font(Res.font.lxgw_wenkai)),
    hei  = FontFamily.Default,
)) { /* … */ }
```

## 模块

```
kheti-core      纯 Kotlin 规则层（零 Compose 依赖，100% 可单测）
kheti-layout    度量/定位/自绘引擎（TextMeasurer + Canvas）
kheti-compose   公共 API：KhetiTheme / KhetiText / KhetiPoem / KhetiFontFamilies
tools/reference-gen   Node 差分金标准生成器（逐字复制上游正则）
reference/       金标准数据、打包字体、渲染证据
docs/            证据与决策文档
```

## 示例 App

跨平台示例（Android + Desktop，共用同一份 `App()` 组合）：

```bash
./gradlew :sample:run          # 桌面窗口
./gradlew :sample:installDebug # 安装到已连接设备
```

可切换：**字体风格**（黑体/宋体/传统）、**正文字号**、**赫蹏规则开关**（对照原生 Compose `Text`）、
**基线网格**、**深色模式**。含 诗（赠汪伦）、词（一剪梅·上下阕）、古文（出师表：首行缩进 + 两端对齐）
与中西文混排对照。

真机截图（Android 16，已在代码中做像素级校验）：

- `reference/app-android.png` —— 全屏
- `reference/app-android-poem.png` —— 诗词区域（四行字列对齐、标点悬挂在列外、行盒严格 30px 网格）
- `reference/app-android-overview.png` —— 缩略全览

## 行间注（ruby）

```kotlin
KhetiText(
    text = "赫蹏是专为中文网页内容设计的排版样式增强。",
    ruby = listOf(KhetiRuby(0, 1, "hè"), KhetiRuby(1, 2, "tí")),  // 下标基于原文
    annotationMode = false,   // false = 内联（不破坏网格）；true = 块模式（.heti--annotation）
)
```

- **内联模式**（上游 `.heti-ruby--inline`）：注音占行盒上半部、基文锚下半部，
  `rt { margin-bottom: -0.25em }` 的 ¼ 字宽重叠也照做 —— 因此 **1.5 网格不被破坏**。
- **块模式**（上游 `.heti--annotation`）：一并套用该 profile —— 行高 **2.25em**、段间距 **0**、首行缩进 **2em**。
- 注音在基文区间上**居中**、字号默认 **50%**。
- 区间下标基于**原文**：引擎会按归一化（吞掉中西文之间的空格）后的下标自动重定位，
  调用方无需关心赫蹏的文本归一化。

证据：`reference/ruby-inline.png`（用霞鹜新致宋渲染的赫蹏官方示例文本）。

## 尚未实现

竖排行间注、iOS 实测（Spike C）、浏览器参考几何抽取（Phase 4）。

## Phase 3：竖排

`KhetiVerticalText`（对应上游 `writing-mode: vertical-rl`）—— Compose 全平台**零竖排支持**，
字形朝向、标点角位、列流全部自研：

- 列序**右 → 左**，`\n` 强制换列（诗词因此天然"一句一列"）
- 列宽 = 行高，字距 `0.125em`（上游 `--vertical` 值）
- 汉字正立、拉丁词与括号旋转 90°、2~3 位数字作**縦中横**（不依赖字体 `vert`/`vrt2`——多数字体没有）
- 标点按**区域规范**就位：大陆靠字身框起始侧、台湾居中（真机像素差异已验证）
- 标点挤压沿竖轴生效（逻辑属性换轴的等价实现）
- 行末标点**悬挂到列尾之外**

真机证据（Android 16）：`reference/app-android-vertical.png` —— 6 列，列距 120px = **30 逻辑 px**（行高网格），
最右列为 3 字标题，诗句各列约 7.4 字格（7 字 + 悬挂标点）。

## 快速开始

```bash
./gradlew :kheti-core:desktopTest                  # 规则层 + 金标准差分
./gradlew :kheti-layout:desktopTest                # 引擎 + 浏览器对拍 + 守卫测试
./gradlew :kheti-layout:connectedDebugAndroidTest  # 真机（Minikin）不变量
adb logcat -d | grep "System.out"                  # 真机证据

# 验收工具（对齐工作的尺子）
node tools/reference-gen/heti-golden.mjs      > reference/heti-golden.tsv       # 规则级金标准
node tools/reference-gen/browser-geometry.mjs > reference/browser-geometry.tsv  # 真实 Chrome + heti@0.9.6 渲染几何
python3 tools/font-coverage/check.py                                            # 字体覆盖守门（缺字会静默回退）
```

需求与验收基线见 **[docs/PRD.md](docs/PRD.md)**；架构决策见 `docs/decisions.md`；
平台实测证据见 `docs/phase0-evidence.md`；逐特性保真度见 `docs/fidelity.md`。

## 工具链

Kotlin 2.4.10 · Compose Multiplatform 1.11.1 · AGP 9.3.1 · Gradle 9.7.0 ·
目标：Android / iOS / Desktop(JVM)。依赖走阿里云镜像（本机 `dl.google.com` 不可达）。

## 许可

移植部分遵循上游 MIT 许可，版权声明见 [`NOTICE`](NOTICE)。
