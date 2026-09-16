# UI 文本与颜色约定（kheti）

> 来源：task `09-16-color-resolution-guard` 与 M0–M8 实施期间的真机缺陷复盘。
> 适用层：kheti-core / kheti-layout / kheti-compose（及任何使用它们的 UI）。
> 这些约定都对应**真机上已发生过的缺陷**，不是风格偏好。

---

## 1. 颜色必须显式解析（Forbidden: 让绘制层回退默认色）

**禁止**把 `Color.Unspecified` 传给绘制层。`drawKhetiText` 遇到 `Unspecified` 会回退到
**平台默认文字色（黑）**，深色模式下即"黑字压深底 → 整片空白"。

- 统一入口：`resolveKhetiColor(color, colors)`（`kheti-compose/KhetiColor.kt`）
- 语义：显式色优先；`Unspecified → colors.ink`
- 同一类坑在**原生 `Material3 Text`** 上同样存在：`MaterialTheme` 只在 `Surface` 内部
  才通过 `LocalContentColor` 适配颜色；示例页曾因此深色模式不可见
- 守卫：`KhetiColorResolutionGuardTest`（明/暗双主题 + 源码扫描）

## 2. 颜色层级（Forbidden: 滥用次级色）

`inkSecondary` **仅允许**用于诗词元信息（上游 `.heti-meta`）与主题定义。

上游依据（`heti.min.css`）：`caption` 与 `figcaption` **都没有 color 声明** —— 图注、表注、
小节标签必须继承正文色。曾经把它们涂成 `inkSecondary`，真机表现为"浅色模式下 12–14sp
文字过灰难读"，同时构成对上游的**保真度偏差**。

守卫：`KhetiColorResolutionGuardTest` 的白名单扫描（白名单 = `KhetiTheme.kt` 定义 +
`KhetiPoem.kt` / `KhetiVerticalPoem.kt` 的元信息）。

## 3. 渲染/度量路径禁止平台 `letterSpacing`（ADR-001）

kheti 的全部间距由引擎**自绘施加**。一旦改用 `TextStyle.letterSpacing`，就会静默退回平台语义，
而 Skia 与 Minikin 对该属性的分配方式不同（实测：Skia 尾侧 X、Android X/2），
跨平台几何等价会无声失效。

守卫：`NoPlatformLetterSpacingGuardTest`（运行时断言 + 源码扫描，白名单仅 Phase 0 探针 `Spike.kt`）。

## 4. 改变字号的样式必须动度量路径

`sup` / `sub` / `small` 会改变 advance。若只在绘制时换样式，后续文字位置会错。
必须：**按缩放后的样式单独度量该字** + 在**缩放边界切段**（否则整段共用一个字号）。
参考实现：`KhetiEngine.measureLine(scale=…)` 与 `buildSegments(scale=…)`。

## 5. 竖排专属坑（均已真机复现）

| 坑 | 规则 |
|---|---|
| 悬挂标点被裁 | 列高**不含**悬挂标点，但**画布高度必须含**（`drawnHeight`）。否则挂出列尾的字形落在画布外 |
| 括号与汉字不居中 | 旋转字形（`Sideways`）必须用**字身框样式**（`lineHeight = fontSize`）并按字身框居中；用整行高样式会被行距空白推偏 |
| 縦中横溢出压字 | 縦中横**仅适用 2 位数**（半角数字宽约 ½em）；3 位以上必须旋转 |
| 网格方向 | 网格由**布局几何**推导：横排画行盒横线、竖排画列盒竖线。不要用页面级固定步长网格 |

## 6. 取证纪律（Pitfalls）

- **不凭印象写规则**。上游值必须取自权威来源；网络不可用时用**本地编译产物**
  （`tools/reference-gen/node_modules/heti/umd/heti.min.css`）或 npm 包，而不是回忆。
- 上游 SCSS 与**已发布 CSS** 可能不一致（例：`QuotePolicy` 曾把"主/次（嵌套层级）"与"横/竖"
  混在 4 个字段里，导致嵌套引号只能是同一种）；以发布产物为准并写断言。
- 参考字体是 **CJK 子集**（ASCII 仅 5/95 码位）：含西文的夹具在两端会**各自回退到不同字体**，
  advance 必然不同。跨端逐字几何比对**必须**使用中西文全覆盖的字体。

## 7. 测试纪律

- **`Density(1f)` 对密度类 bug 是盲的**：`px.sp` 这类错误在密度 1 下不可见，真机（密度 4）
  会把注音字号放大 4 倍。涉及尺寸/缩放的改动必须补**高密度断言**（参考 `M8ScaleInlineTest`、
  以及密度 2 的注音回归测试）。
- **像素启发式必须配色方案感知**：曾用"暗像素 = 墨迹"的脚本在深色模式下把整个背景判成墨迹，
  报出"内容区空白"的假结论。断言前先探测背景亮度，再决定墨色方向。
- 断言优先用**可执行命令/数值**（如"差异 277px""空间占位 ≤1 字"），少用"看起来对"。
- 验证像素/几何时**避免自证式断言**：期望值不要用与实现相同的公式推导（曾因此在块模式注音
  重叠时测试仍然通过）。改为从字体度量/渲染结果反推期望。
