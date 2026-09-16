# kheti PRD（产品需求与验收对账）

> 项目：**kheti** —— 赫蹏（heti）中文排版规则在 Kotlin / Compose Multiplatform 的移植
> 上游：[sivan/heti](https://github.com/sivan/heti)（MIT, Copyright (c) 2020 Sivan）
> 本文是**唯一的需求与验收基线**：新需求先写进本文，完成一项勾一项。
> 配套文档：`docs/decisions.md`（架构决策）、`docs/phase0-evidence.md`（平台实测证据）、`docs/fidelity.md`（逐特性保真度账本）。

---

## 1. 目标与非目标

**目标**：让 Android / macOS(Desktop) / Web 上渲染的中文诗词与古籍排版，
在**几何规则层面**与赫蹏一致，且**三端彼此一致**；古诗词的竖排为第一等公民。

**非目标（明确不做）**：像素级复刻某个浏览器截图；可编辑文本（IME）；BiDi 完整等价。

**"还原度"的定义**（避免无意义争论）：
- 赫蹏的效果只由两类原语构成 —— **P1 单字 advance 增量**（中西文 ¼em、标点 −½/−¼em）与
  **P2 区块几何**（16px/1.5 网格/42em/段距/缩进）。
- 验收锚点 = 与**真实赫蹏在真实浏览器中的渲染几何**逐字符比对（§4 对拍），
  以及三端在同一字体下的几何一致性。
- 像素级一致只在**同一字体**前提下成立；跨字体不可能，因为赫蹏自身依赖系统字体栈。

---

## 2. 范围与状态

### 2.1 v1 功能范围（已确认）

| # | 需求 | 状态 | 验收证据 |
|---|---|---|---|
| F1 | 标点挤压（−½em / −¼em） | ✅ | 21 夹具差分金标准逐字符一致；真机/桌面实测 −0.5em+字距 |
| F2 | 中西文 ¼em 间距 + 吞掉手敲空格 | ✅ | 同上；浏览器对拍 gap = 4.00px @16px |
| F3 | 诗词版式（居中/无缩进/宋楷/1.5 网格） | ✅ | 几何 + 像素 + 真机（行距恒 120px = 30 逻辑px） |
| F4 | 竖排 vertical-rl（列右→左、一句一列） | ✅ | 真机 6 列、列距=行高 30 逻辑px；排版层单测 |
| F5 | 竖排字形朝向（正立/旋转/縦中横） | ✅ | 朝向表单测；縦中横限 2 位数 |
| F6 | 竖排标点区域规范（大陆靠角 / 台湾居中） | ✅ | 断言 CN(+½,−½)em、TW(+¼,−¼)em；真机像素差 1144px |
| F7 | 标点悬挂（横排不计行宽 / 竖排挂出列尾） | ✅ | 像素：横排溢出 4–5px；竖排画布覆盖悬挂字形 |
| F8 | 行间注 ruby（横排内联 + 块两模式；竖排在基文右侧） | ✅ | 横排：像素差分（注音在基文之上、居中、半字号）；竖排：几何 + 像素（右侧 56px 墨迹）、画布预留 |
| F9 | 着重号（`heti-em`） | ✅ | 像素差分：圆点仅出现在被标记字下方、中心低于基文中线（`KhetiText(emphasis=…)`） |
| F10 | 古文首行缩进 2em | ✅ | 断言 32px @16px，且扣减首行可用宽度 |
| F11 | 段间距（折叠后 = 1 网格）与首/末元素边距清零 | ✅ | 断言折叠后 24px、首块 top=0 |
| F12 | 三种字体风格（黑/宋/传统）+ 字号档 + 标题档 | ✅ | 断言传统=正文宋体+标题楷体 800 |
| F13 | 深色模式自适应 | ✅ | `KhetiTheme` 跟随 `isSystemInDarkTheme()` |
| F14 | 排版网格可视化（横排画行盒 / 竖排画列盒） | ✅ | 真机：竖排网格线间距恒 120px |

### 2.1b 上游演示站（sivan.github.io/heti）功能对账

| # | 演示页功能 | kheti 状态 |
|---|---|---|
| D1 | 字体风格切换（黑体/宋体/传统） | ✅ `KhetiFlavor` |
| D2 | 深色模式（auto/light/dark） | ✅ `KhetiTheme`（跟随系统；手动覆盖由调用方传入） |
| D3 | 古文 `--ancient` | ✅ 宋体 + 首行缩进 2em |
| D4 | 诗词 `--poetry` | ✅ 居中、无缩进、楷体标题、悬挂、meta |
| D5 | 行间注 `--annotation` | ✅ ruby + 2.25 行高 + 段距 0 + 缩进 2em + 着重号 |
| D6 | 竖排 `--vertical` | ✅ 列右→左、区域标点、縦中横、悬挂、ruby 在右侧 |
| D7 | 增强脚本 `autoSpacing()`（中西文 + 挤压） | ✅ 引擎内建（不依赖 DOM），对拍 0.03px |
| D8 | 行内 ruby（`heti-ruby--inline`） | ✅ 内联模式，不破坏网格 |
| D9 | 网格贴合 + 网格可视化 | ✅ 由布局几何推导（横排行盒 / 竖排列盒） |
| D10 | 元信息 `.heti-meta`、字号 helper | ✅ |
| D11 | **多栏排版** `--columns-2/3/4`、`--columns-Nem` | ✅ **完成**：`KhetiColumns(text, count=…)`（按栏数）与 `KhetiColumns(text, count=null, columnWidthEm=…)`（按栏宽，栏数由可用宽反推）。实现复用现有引擎（先排单栏 → 按 `column-fill: balance` 切分**整行** → 每栏子布局行 top 归零 → 按栏位平移绘制），因此 ruby/行内样式/悬挂/网格在各栏自动生效。断言：均衡（行数差 ≤1，columns-2→[3,3]、3→[3,2,2]、4→[2,2,2,1]）、不丢行、每栏首行 top=0、整幅宽 = 栏宽×N+间距、按宽反推（800/200/20→3 栏；放不下→1 栏；缺省/非法→1 栏） |
| D12 | **文章块级样式**：blockquote / figure·figcaption / hr / pre·code / table / ul·ol（含 `heti-list-latin`、`heti-list-han`）/ h1–h6 具体边距字距 | ✅ **完成**：`KhetiHeading`（h1–h6；**修正：基础字重 600 而非 800**（800 只属诗词/古文 modifier）；h1–h3 额外 `letter-spacing:.05em`；margin-block 24/12、h1 底边距 24）、`KhetiBlockquote`（alpha .054 + 2em/1em）、`KhetiHr`（30%/1px/2 网格）、`KhetiPre`（等宽+背景，**刻意不做赫蹏间距** —— 上游跳过 `pre`/`code`）、`KhetiList`（bullet/decimal/cjk-ideographic/upper-latin/lower-roman）、`KhetiFigure`·`KhetiFigcaption`（figure 居中、图注 14/24 且文字 start）、`KhetiTable`（1px 边框 #ccc、单元格 6/8、居中、**表注在下方**）；值取自上游编译产物 `heti.min.css`（网络不可用时用本地 npm 包取权威值），全部有断言 |
| D13 | **行内元素样式**：a / abbr / u（专名号）/ del·ins·s / dfn / mark / code / q / sup·sub / small | ✅ **完成**：装饰类（底色/下划线/点线/删除线/斜体）+ `q` 自动加引号（`wrapQuotes`：cn 弯引号／tw·common 直角引号、竖排换竖排引号集合、嵌套主次层级、下标映射）+ **sup/sub/small**（按缩放样式单独度量、缩放边界切段、绘制换样式与基线位移；断言 advance 10.67→8.54/9.34、后续字左移、上标墨迹 y=6..15 高于正文 13..24）+ 公共 API `KhetiText(inline=…)` |
| D14 | **脚注系统** `.heti-fn`（上标锚点 + 页脚列表 + `:target` 高亮） | ❌ 未实现（`sup` 已就绪，剩点击跳转状态与页脚列表） |
| D15 | **英文排版** `lang="en-US"` / 非中文容器（字距归零、`text-align: start`） | ✅ `KhetiLang.Zh/Latin`：西文容器字距归零（断言差值 = 5×0.02em） |
| D16 | 演示站点自身的面板（网格/深色/字体开关、锚点 `#`） | ➖ 属站点 UI 而非库能力；示例 App 已有等价开关 |

### 2.2 平台与对齐（当前主线）

| 平台 | 引擎 | 状态 |
|---|---|---|
| macOS / Desktop(JVM) | Skia/Skiko | ✅ 已验收（全量单测 + 示例窗口 + 浏览器对拍） |
| Android | Minikin | ✅ 真机不变量测试 + 示例 APK |
| Web | 浏览器原生 | ➖ **不做**：heti 本身就是浏览器方案（CSS + JS），Web 直接引入上游即可；kheti 的价值在 heti 无法运行的原生端，复刻属于重复造轮子 |
| iOS | Skia | ⏸ **最后人工验收**（代码有 target，未实测） |

**对齐策略**：Android(Minikin) 与 macOS(Skia) 是两条不同的文本引擎路径，因此对齐的核心不是"猜参数"，而是：
1. **不使用**平台的 `letterSpacing`/`justify`/断行几何，只把平台当作**字形宽度来源**（ADR-001）；
2. 用**同一字体**在两端跑**同一组几何断言**；
3. 用浏览器对拍给出"与赫蹏参考实现"的偏差上界。

### 2.3 验收标准

| # | 标准 | 状态 |
|---|---|---|
| A1 | 逐字增量 == 赫蹏规则（规则级） | ✅ 21 夹具差分金标准 |
| A2 | 逐字几何 == 真实浏览器渲染（渲染级，纯 CJK） | ✅ 最大偏差 **0.03px** |
| A3 | 行盒/列盒贴合网格 | ✅ 断言 + 真机 |
| A4 | 竖排列序右→左、列距=行高 | ✅ |
| A5 | Android 与 macOS 在**与字体无关的量**上一致 | ✅ 真机 4 项 |
| A6 | 同一字体下 Android 与 macOS 逐字几何相等 | ⏳ 需补**中西文全覆盖字体**（参考字体 ASCII 仅 5/95 码位） |
| A7 | **渲染路径禁止平台 letterSpacing** 守卫 | ✅ `NoPlatformLetterSpacingGuardTest`（运行时 + 源码双检） |
| A8 | 字体覆盖守门（避免缺字静默回退） | ✅ `tools/font-coverage/check.py` |

---

## 3. 已知缺口与偏差

| 项 | 说明 | 计划 |
|---|---|---|
| 文章/散文模式 | 标题层级、列表、表格、`column-count` 分栏未接入 | 未排期 |
| 参考字体缺西文 | `reference/fonts` 是 CJK 子集（ASCII 仅 5/95 码位）→ 含西文夹具两端**回退到不同字体**，累计偏差达 3.7px | **对齐任务必须先补一个中西文全覆盖字体**，否则 A6 无法达成 |
| 打包字体覆盖不足 | 5.3k 字子集缺 `蹏/〔〕/懈/殂`；示例默认用系统字体 | 已加守门工具；需要时换全量字体 |
| `hyphens: auto` | 平台字典差异，接受偏差 | — |
| 边缘 lookahead 差异 | 上游按 DOM 文本节点做正则，我们按整串 | Phase 4 若升级 jsdom 直跑上游再收口 |
| 像素级跨端一致 | 光栅化差异（CoreText/Skia/Minikin）不可消除 | 不承诺 |

---

## 4. 验收手段（对拍）

```bash
node tools/reference-gen/heti-golden.mjs      > reference/heti-golden.tsv        # 规则级金标准
node tools/reference-gen/browser-geometry.mjs > reference/browser-geometry.tsv   # 渲染级参考几何
python3 tools/font-coverage/check.py                                             # 字体覆盖守门
./gradlew :kheti-core:desktopTest :kheti-layout:desktopTest :kheti-compose:desktopTest
./gradlew :kheti-layout:connectedDebugAndroidTest   # 真机
```

- **规则级**：Node 端用**逐字复制上游正则**的独立实现产出期望值，Kotlin 端逐字符断言（不等则改 Kotlin）。
- **渲染级**：真实 `heti@0.9.6` 的 `heti.min.css` + `heti-addon.min.js`，在**本机 Chrome**（playwright-core 驱动，
  不下载浏览器）里跑 `autoSpacing()`，用 `Range.getBoundingClientRect()` 抽逐字 x。
- 两者结合：规则级保证"规则没抄错"，渲染级保证"几何真的对得上"。

---

## 5. 里程碑

| 阶段 | 内容 | 状态 |
|---|---|---|
| M0 | 平台能力实测 + 架构决策（ADR-001/002/003） | ✅ |
| M1 | 规则层（`kheti-core`） | ✅ |
| M2 | 横排引擎 + 诗词 API + 示例 App | ✅ |
| M3 | 竖排引擎 | ✅ |
| M4 | 行间注 | ✅ |
| M5 | 对拍尺子 + ADR 守卫 + Android/macOS 对齐收口 | ✅（A6 待全覆盖字体） |
| M6 | 残余功能补齐：着重号实测、竖排行间注 | ✅ |
| M7 | iOS 人工验收（最后一步） | ⏸ |
| M8 | **文章模式**（对齐演示页 D11–D15）：块级样式、行内元素、多栏、脚注、`lang` 字距 | ⏸ 待排期 |
