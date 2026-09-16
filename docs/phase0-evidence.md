# Phase 0 证据：平台文本能力实测

> 全部数字来自本机/真机实测，不是文献推断。复现命令见每节末尾。
> 环境：macOS（Skiko/Skia m144 系，CMP 1.11.1）｜真机 PJZ110 / Android 16｜
> Kotlin 2.4.10｜AGP 9.3.1｜Gradle 9.7.0｜字体：平台默认（未打包）。

---

## 1. Spike A：`SpanStyle.letterSpacing` 到底怎么分配？

方法：文本 `A中B`，只给中间的「中」加 `letterSpacing = X`，用**浮点光标位**拆出首/尾增量。

### 实测结果

| 平台 | 请求 X | 首位移动 | 尾增量 | 整行Δ |
|---|---|---|---|---|
| **Desktop / Skia** | 4.000 | 0.000 | **4.000** | 4.000 |
| Desktop / Skia | 0.320 | 0.000 | **0.320** | 0.320 |
| Desktop / Skia | −4.000 | 0.000 | −4.000 | −4.000 |
| **Android / Minikin** | 4.000 | 0.000 | **2.000** | 4.000 |
| Android / Minikin | 0.320 | 0.000 | **0.160** | 0.320 |
| Android / Minikin | −4.000 | 0.000 | −2.000 | −4.000 |

行首场景（`[中]A`，X=4）：

| 平台 | 首位移动 | 尾增量 | 整行Δ |
|---|---|---|---|
| Desktop / Skia | 0.000 | 4.000 | 4.000 |
| Android / Minikin | 0.000 | **0.000** | **2.000** |

### 结论

1. **两个平台的语义确实不同**：Skia 把整份 X 作为**尾侧**增量（恰好等价于 `margin-inline-end: X`），
   Android 只给 **X/2** 尾增量、另外 X/2 落在该字自身之前（行首还会被剥离）。
   → **同一份 `SpanStyle` 在两端产生不同几何**，无法满足"跨平台几何等价"。
   这就是 ADR-001 拒绝「方案 A」的直接实测依据（不再只是源码推断）。
2. **"Android 把 letterSpacing 四舍五入到整像素"未能在本机复现**：请求 0.320px 时实测
   尾增量 0.160px、整行Δ 0.3199997px，**没有量化到 0**。
   → 该说法出自 2015 年 CM-12 分支的 Minikin 源码，**对 Android 16 + Compose 1.11 不成立**，
   已从 ADR 的理由中降级为"未复现的历史风险"。
   （保留的教训：文献结论必须实测验证。）
3. 即便如此，方案 A 仍被否决——理由 1 已经足够。

---

## 2. Spike B：整行度量 → 逐字定位 → 分段自绘

方法：`借指纸。《汉书`（含 `。《` 挤压对）。先用 `TextMeasurer` 取逐字浮点光标位；
再分别"整行一次绘制"与"逐字分段绘制"到离屏 `ImageBitmap`；最后施加赫蹏增量重绘并量墨迹宽度。

### 实测结果（两端完全一致）

| 指标 | Desktop / Skia | Android / Minikin |
|---|---|---|
| 逐字光标位单调 | ✅ | ✅ |
| 最大 advance 误差 | 0.0000 px | 0.0000 px |
| 整行绘制 vs 逐字分段绘制 | **像素差异 0** | **像素差异 0** |
| 墨迹宽度（未调整 → 调整后） | 111 → 103 px | 111 → 103 px |
| 实测差 vs 期望差 | −8 px vs **−8.00 px** | −8 px vs **−8.00 px** |

### 结论

**方案 B（自绘定位引擎）在两端给出逐像素一致的结果**，并且精确复现了赫蹏的
`−0.5em` 标点挤压（16px 字号下 −8px）。

- 逐字绘制不会丢失跨字 shaping（CJK 场景下差异为 0）；
- 逐字光标位精度足以承担亚像素增量（0.25em = 4px、0.125em = 2px 均可表达）；
- 这直接支撑"跨平台几何等价"这一验收标准。

> 注意：拉丁文连字/字距调整（kerning）在逐字拆分后理论上可能损失。CJK 文本不涉及；
> 混排的西文串应按"整段一次绘制"处理，只在**调整点**切段（这也是计划中的实现方式，
> 逐字绘制只是本探针的最坏情况验证）。

---

## 3. 规则层：与赫蹏上游的差分等价

`AdjustmentPlanner`（Kotlin）与 `tools/reference-gen/heti-golden.mjs`（Node，**逐字复制上游正则**）
在 21 条夹具上逐字符一致：归一化文本、逐字 leading/trailing 增量、被吞掉的空格数全部相等。

- `:kheti-core:desktopTest` → **15/15 通过**（含金标准比对）。
- 过程中修正的两个真实缺陷（都会导致偏离赫蹏）：
  1. `heti-spacing-start` 只有**尾侧**边距、`heti-spacing-end` 只有**首侧**边距——最初两边都加了；
  2. 被 spacing 包裹的字符会**跳过**挤压趟次（等价上游 `HETI_SKIPPED_ELEMENTS`）。
- 记录的一条赫蹏冷知识（已固化为测试）：**`·`（U+00B7）落在赫蹏的 `A` 区间 `\u0080-\u00ff` 内**，
  因此它按"西文"处理、触发 ¼ 字宽中西文间距，而**不会**触发间隔符挤压；
  真正的间隔符只有 `‧`（U+2027）与 `・`（U+30FB，落在 CJK 区）。

---

## 4. 平台能力核查（版本级事实，2026-09 核实）

| 事实 | 结论 |
|---|---|
| CMP 1.12.0（stable, 2026-08）/ 1.13.0-alpha01 CHANGELOG | **零**处 vertical text / ruby / 标点挤压 / CJK 字距条目 |
| androidx Compose UI | stable 1.12.1（本工程用 CMP 1.11.1，与 songci 对齐） |
| Skiko 内置 Skia | m153 系 —— **上游 `skia/main` 不等于 CMP 实际 API 面** |
| `TextAlign.Justify`（Android） | 只映射 `JUSTIFICATION_MODE_INTER_WORD`；Minikin `isWordSpace(c){return c==' ';}` → **无 ASCII 空格的 CJK 不会被拉伸** |
| `TextAlign.Justify`（Skia） | `TextLine::justify()` 按 `isIdeographic()` 拉伸，**顿号/句号不算表意文字**；只拉不压、末行不处理 |
| 标点挤压 / hanging punctuation | Minikin 与 SkParagraph **均无**（Android 16 也未加） |
| 竖排 | SkParagraph/Skiko/Compose common **全无**；仅 Android 有 `androidx.text:text-vertical` 1.0.0-beta01（display-only，且不含挤压） |
| `vert`/`vrt2`/`vpal` | 在横排 Compose 文本中 **no-op**（竖排布局特性） |
| `fontFeatureSettings` | 是**字符串参数**（无 `FontFeatureSettings` 类）；Android 与 Skiko 均会传给 HarfBuzz → `palt`/`halt` 是唯一"字体级"可用旋钮（前提是字体带该特性） |
| Ruby | 无内建；Android 有 `RubySpan`/`EmphasisSpan`（`ReplacementSpan`，横排亦可用，但 Android-only） |
| 第三方 | 无 CMP 竖排库、无 kinsoku/标点挤压库、**无任何 heti 非浏览器移植**（本项目为首例） |

参考：[CMP CHANGELOG](https://github.com/JetBrains/compose-multiplatform/blob/master/CHANGELOG.md)、
[clreq](https://www.w3.org/TR/clreq/)、[clreq 差距分析](https://www.w3.org/TR/2024/DNOTE-clreq-gap-20240919/)、
[MDN text-spacing-trim](https://developer.mozilla.org/en-US/docs/Web/CSS/text-spacing-trim)、
[googlefonts/chws_tool](https://github.com/googlefonts/chws_tool)、
[luatex-cn](https://ctan.org/tex-archive/language/chinese/luatex-cn)（clreq 符合度：横排 ~73% / 竖排 ~63%）。

---

## 5. 尚未完成 / 未决

1. **iOS（Kotlin/Native + Skiko）实测未跑**：预期与 Desktop 同为 Skia 路径，但必须实测确认
   （`iosSimulatorArm64Test`）。这是 Phase 0 唯一未闭环项。
2. **竖排引擎**：`FillBoundingBoxes`/逐字定位的竖排变体尚未验证（Phase 3）。
3. **字体特性**：霞鹜文楷 / 霞鹜新致宋是否带 `palt`/`halt` 未检测（决定是否保留该加速路径）。
4. **区域规范默认值**（CN 靠起始侧 / TW 居中）待产品确认。

## 6. 复现方式

```bash
./gradlew :kheti-core:desktopTest                 # 规则层 + 金标准差分（15 项）
./gradlew :kheti-layout:desktopTest               # Spike A/B（Desktop/Skia）
./gradlew :kheti-layout:connectedDebugAndroidTest  # Spike A/B（真机 Minikin）
adb logcat -d | grep "System.out"                  # 真机打印的证据
node tools/reference-gen/heti-golden.mjs > reference/heti-golden.tsv  # 重新生成金标准
```
