# kheti 架构决策记录（ADR）

> 项目：kheti —— 赫蹏（heti）中文诗词排版在 Kotlin / Compose Multiplatform 的移植
> 上游：[sivan/heti](https://github.com/sivan/heti)（MIT, Copyright (c) 2020 Sivan）
> 状态：Phase 0 决策已定；日期见 git 记录。

---

## ADR-001：主引擎采用「平台度量 + 自绘定位」，而非 `Text` + `SpanStyle.letterSpacing`

**状态：已接受（Phase 0 证据支持）**

### 背景

赫蹏的排版效果只有两类原语：

- **P1 单字 advance 增量**：中西文 ±0.25em；标点挤压 −0.5em / −0.25em。
  上游实现是 JS 给匹配文本套 `<heti-spacing>` / `<heti-adjacent>` 元素，靠 CSS
  `margin-inline-start/end` 施加**单侧**边距（`lib/helpers/_add-on.scss`）。
- **P2 区块几何**：16px 字号、1.5 行高网格（24px）、42em 行宽、段距 0.5/1 网格、居中/缩进。

### 决策

自建度量层：用 `TextMeasurer` 取**逐字光标位**（Float），把 P1 增量作为**纯几何偏移**累加，
再用 `DrawScope.drawText` 按调整点分段绘制。渲染路径**禁止出现任何非零 `letterSpacing`**（有守卫测试）。

### 理由（三条独立证据）

1. **`letterSpacing` 的语义与赫蹏需要的语义不同，且两端不一致。**
   - **实测（Phase 0 Spike A，见 `docs/phase0-evidence.md`）**：给单个字加 `letterSpacing = X`，
     - Desktop/Skia：首位移动 0，**尾增量 = X**（恰好等价于 `margin-inline-end: X`）；
     - Android/Minikin：首位移动 0，**尾增量 = X/2**，另一半落在该字之前；行首时该字尾增量变 0、整行Δ = X/2。
     → **同一份 `SpanStyle` 在两端产生不同几何**，跨平台等价无法成立。
   - 源码侧解释：Minikin `Layout::doLayoutRun` 把 `letterSpace` 拆成 `halfLeft = floor(letterSpace*0.5)`
     与 `halfRight`，分别在 shaping run 首/尾施加；AOSP `Paint.java` 的
     `TEXT_RUN_FLAG_LEFT_EDGE/RIGHT_EDGE` 即用于在行首/行尾剥离这两个"外半边"。
     Skia 侧 `Run::addLetterSpacesEvenly` 在每个 cluster 后加一份 space，`Cluster::setHalfLetterSpacing(space/2)`
     配合 `TextLine` 的 `fShift += getHalfLetterSpacing()` 做半宽补偿。
     证据：[Minikin Layout.cpp](https://android.googlesource.com/platform/frameworks/minikin/+/refs/heads/main/libs/minikin/Layout.cpp)、
     [Skia Run.cpp](https://raw.githubusercontent.com/google/skia/main/modules/skparagraph/src/Run.cpp)、
     [Skia TextLine.cpp](https://raw.githubusercontent.com/google/skia/main/modules/skparagraph/src/TextLine.cpp)。

2. **（已修正）关于"Android 像素量化"的说法未获实测支持。**
   文献（2015 年 CM-12 分支 Minikin 的 `round(letterSpace)`）推断 Android 会把 letterSpacing 量化到整像素，
   但**在 Android 16 真机上未复现**：请求 0.320px 时实测尾增量 0.160px、整行Δ 0.3199997px。
   该风险从 ADR 理由中降级；方案 A 仍因理由 1 被否决。
   **保留的教训：文献结论必须实测验证，不能直接写进设计前提。**

3. **平台完全不提供赫蹏所需的其它能力**（截至 CMP 1.12.0 / androidx Compose UI 1.12.1）：
   - 无任何 inline 边距 API（`Placeholder` 宽度不可为负）。
   - `TextAlign.Justify` 在 Android 只映射 `JUSTIFICATION_MODE_INTER_WORD`，Minikin 的
     `isWordSpace(c) { return c == ' '; }` 意味着**无 ASCII 空格的 CJK 根本不会被拉伸**；
     Skia 的 `TextLine::justify()` 只按 `isIdeographic()` 在表意文字之间拉伸（**顿号/句号不算表意文字**），
     且只拉不压、末行不处理。→ CJK 两端对齐与标点挤压都必须自研。
   - 无 hanging punctuation、无标点挤压（`text-spacing` / `text-autospace`）支持。
   - **无竖排**：SkParagraph 无 writing-mode；CMP 全平台无竖排 API（CHANGELOG 从 1.9 到 1.13.0-alpha01 零命中）。
     Android 独有 `androidx.text:text-vertical`（1.0.0-beta01，`VerticalText`/`RubySpan`/`EmphasisSpan`），
     但仅 Android、display-only、且**不含标点挤压**——采用它会直接破坏跨平台几何等价。
   - `vert`/`vrt2`/`vpal` 在横排 Compose 文本中是 no-op（它们是竖排布局特性）。

### 被否决的替代方案

| 方案 | 否决理由 |
|---|---|
| `SpanStyle.letterSpacing` 冒充边距 | 见理由 1/2：语义为双侧、跨平台系数不同、Android 像素量化 |
| 依赖字体 `palt`/`halt` 做标点挤压 | 多数 CJK 字体缺这两个特性（Google 专门做 [chws_tool](https://github.com/googlefonts/chws_tool) 来补）；中文（非日文）字体尤其缺。**保留作为可选加速路径**（`fontFeatureSettings` 字符串参数在 Android 与 Skiko 均会传给 HarfBuzz，已核实） |
| 用 Unicode 零宽字符 / U+FE00 变体选择符"预压缩" | 两个引擎对它们均无反应（inert） |
| 直接用 Android `androidx.text:text-vertical` | Android-only，破坏跨平台几何等价；且不含挤压 |
| 复用现成 Kotlin/CMP 竖排或挤压库 | **不存在**：无 CMP 竖排库、无 kinsoku/标点挤压库、无任何 heti 非浏览器移植（见 `docs/phase0-evidence.md` 调研） |

### 后果

- **收益**：几何完全由我们掌控 → 跨平台规则级等价、亚像素精确、可注入赫蹏任意规则；
  且竖排/悬挂/着重号/行间注共用同一套定位结果。
  **Phase 0 Spike B 实测**：两个平台上"整行绘制 vs 逐字分段绘制"像素差异均为 **0**，
  且施加 `−0.5em` 挤压后墨迹宽度收缩 **−8px（期望 −8.00px）**——两端结果完全一致。
- **代价**：必须自己实现选择/命中/无障碍映射（`Modifier.semantics { text = ... }`），
  且**不支持 IME 编辑**（与 Android 官方竖排同一定位：display-only）。
- **约束**：渲染路径不得使用 `letterSpacing`；平台只作为**字形度量来源**。

---

## ADR-002：`kheti-core` 不依赖 Compose

**状态：已接受**

规则层（字符类、5 趟规则、度量常量、引号/区域策略）是纯 Kotlin，零 Compose 依赖，
因此可在 `commonTest` 100% 覆盖，并在 JVM 上毫秒级回归。Compose 只在 `kheti-layout` / `kheti-compose` 出现。

---

## ADR-003：规则等价性用「差分金标准」证明

**状态：已接受**

上游规则的权威实现是 `js/heti-addon.js`。我们的做法：

1. `tools/reference-gen/heti-golden.mjs` —— 用**逐字复制的上游常量与正则**在 Node 独立实现一遍 5 趟替换，
   输出 `reference/heti-golden.tsv`（归一化文本 + 逐字 leading/trailing 增量）。
2. `kheti-core` 的 `GoldenEquivalenceTest` 读该文件逐字符断言。

两个独立实现在同一份规范下必须给出相同结果；若不一致，**改 Kotlin，不改金标准**。

### 已知偏差（记录在案，非缺陷）

- **跨包裹边界的 lookahead**：上游在 DOM 上按文本节点做正则，若前一趟已在查询字符处插入包裹元素，
  则跨元素的 lookahead 会失效。本实现（以及金标准生成器）在整串上做正则，因此对
  `·《〈` 这类"前一趟已包裹、后一趟仍需跨过去看"的病态序列会比上游多挤压一次。
  日常文本与诗词不触发；Phase 4 若升级为 jsdom 直跑上游代码，将补一条针对性断言。
- 代理对（emoji 等）按 UTF-16 码元处理，与上游 JS 的 `textContent` 索引语义一致。

---

## ADR-004：工程选型

**状态：已接受**

| 项 | 决定 | 理由 |
|---|---|---|
| 工具链 | Kotlin 2.4.10 / CMP 1.11.1 / AGP 9.3.1 / Gradle 9.7.0 | 与同工作区的 `songci`（真实消费方，宋词 App）完全一致，**已在本机验证可构建**；Kotlin/Native 2.4.10 工具链已缓存 |
| JDK | `~/.gradle/gradle.properties` 已固定 `org.gradle.java.home` → 由 Gradle 供应的 JDK 21 | 本机 shell 的 JDK 25 对 Gradle 9.7 有兼容风险 |
| 依赖源 | 阿里云 `google` + `central` 镜像优先，CI 用官方仓库 | **dl.google.com 在本机不可达**（实测 000），阿里云镜像可用（已验证 AGP/androidx pom 200）|
| Gradle 分发 | 腾讯镜像 + 本地已缓存 9.7.0 | 免下载 |
| 目标平台 | androidTarget + jvm("desktop") + iosArm64 + iosSimulatorArm64 | CMP 1.11 起不再发布 `iosX64` 构件 |
| 字体 | 默认系统字体栈；验收套件用打包 OFL 字体 | `songci` 已内置 **霞鹜文楷**（楷体）与 **霞鹜新致宋**（宋体），均为 OFL-1.1，可直接复用 |
| 许可 | `kheti-core` 保留 Sivan 的 MIT 声明；仓库根放 `NOTICE` | 规则移植属 MIT 所述 "substantial portion" |

---

## 待决事项（需 Phase 0 数据或产品输入）

1. **竖排区域规范默认值**：`KhetiRegion.CN`（标点靠字身框起始侧）还是 `TW`（居中）？
   赫蹏竖排默认走台湾引号规范（`$chinese-quote-set: "common"`）。
2. **是否把 `palt`/`halt` 作为可选加速路径**：需先检测霞鹜文楷/新致宋是否真的带这两个特性。
3. **CMP 版本**：是否从 1.11.1 升到 1.12.0（stable）——升级收益目前不明确，暂缓。
