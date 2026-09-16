# 赫蹏 → kheti 保真度账本

> 逐特性对照上游赫蹏，记录**达成度**与**实现方式**。
> 判定标准（用户选定）：几何/规则级等价 —— 间距、行盒、断行规则与赫蹏一致，可用测试断言。
> 更新于 Phase 2 完成时。

## 已实现并验证

| 赫蹏特性 | 上游实现 | kheti 实现 | 达成度 | 验证方式 |
|---|---|---|---|---|
| 中西文 ¼ 字宽间距 | JS + `margin-inline-start/end: .25em` | `AdjustmentPlanner` → 逐字增量 → 自绘 | **规则级 100%** | 21 条夹具差分金标准（逐字符）+ 引擎几何断言 |
| 全角标点 ½ 字宽挤压 | JS + `margin-inline-end: -.5em` | 同上 | **规则级 100%** | 同上；净效果 = −0.5em + 字距 |
| 间隔符/弯引号 ¼ 字宽挤压 | JS + quarter 类 | 同上 | **规则级 100%** | 同上 |
| 吞掉中西文之间的手敲空格 | JS `textContent.trim()` | 归一化文本 + `removedSpaces` | **100%** | 金标准含原文/归一化文本两列 |
| 字号档（12/14/16/18/20px） | `heti-small` 等 helper | `KhetiMetrics.Size` | **100%** | 常量单测 |
| 行高网格（16px→24px） | `line-height: 1.5` | `lineHeight` + 行盒 = 网格 | **100%** | 断言 `lineTop == i × 24` |
| 字距 0.02em / x-large 0.05em | CSS 层叠 | 引擎逐字施加 | **100%**（含 gap 例外） | 引擎几何断言 |
| `heti-spacing` 区间字距为 normal | 匹配规则优先于继承 | 引擎 gap 例外分支 | **100%** | 引擎几何断言 |
| 诗词版式：居中、无缩进 | `.heti--poetry p` | `KhetiAlignment.Center` | **100%** | 居中几何断言 |
| 标点悬挂 | `.heti-hang`（绝对定位，不占行宽） | `KhetiHang.LineEnd` | **100%**，且**自动化**（超集） | 像素级：墨迹溢出内容宽度 4–5px；四行字列对齐 |
| CJK 两端对齐（字间拉伸） | `text-align: justify`（浏览器按字间分配） | `KhetiAlignment.Justify` | **100%**（平台做不到，故自研） | 非末行填满行宽、末行不拉伸 |
| 断行 + 行首禁则 | 浏览器断行 | 平台建议 + 我们校验/缩短/贪心延长 + 禁则断言 | **规则级 ~95%** | 断言行首无禁则标点 |
| 首行缩进 2em（古文） | `.heti--ancient p` | `firstLineIndentEm`（并扣减首行可用宽度） | **100%** | 缩进 = 2em = 32px 断言 |
| 着重号（`heti-em`） | `text-emphasis` | `drawKhetiText(emphasis = …)` 自绘圆点 | 已实现（几何来自引擎） | 绘制路径已有 |
| 段间距（顶 0.5 网格 / 底 1 网格） | `p { margin-block }` | `KhetiBlockSpec` + **CSS 外边距折叠**（取最大 → 24px） | **100%** | 折叠后 = 24px 断言；首元素顶边距清零 |
| 首/末元素边距清零 | `> *:first-child/:last-child` | `layoutKhetiBlocks` 首块顶边距清零、末块底边距不计入高度 | **100%** | 断言首块 top = 0 |
| 三种字体风格（黑/宋/传统） | `--sans/--serif/--classic` | `KhetiFlavor` + `bodyFamily`/`headingFamily` | **100%**（需注入字体才有真观感） | 断言传统风格=正文宋体+标题楷体 |
| 标题档（h1~h6） | `_variables.scss` | `KhetiMetrics.Heading` + `KhetiTextStyle.heading` | **100%**（字号/行高） | 常量来自上游 |
| 深色模式自适应 | `prefers-color-scheme` | `KhetiTheme` 默认跟随 `isSystemInDarkTheme()` | **100%** | — |
| 行宽上限 42em | `.heti { max-width: 42em }` | `maxLineLengthEm`（与父约束取较小值） | **100%** | — |
| **行间注（ruby）** | `<ruby>` + `.heti-ruby--inline` / `.heti--annotation` | `KhetiRuby` 区间 + 引擎注音几何 + 自绘 | **规则级 100%**（横排） | 注音居中、半字号、两模式行高；像素差分验证 |
| 行间注·内联模式行高 | `.heti-ruby--inline { height: 1.5em }` | 注音占行盒上半部，基文锚下半部 | **100%** | 断言行高仍为 24px 且注音不溢出 |
| 行间注·块模式 | `.heti--annotation { p { line-height: 2.25; margin: 0; text-indent: 2em } }` | `annotationMode = true` 一并套用该 profile | **100%** | 断言行高 36px、注音完整位于基文上方 |
| 注音区间与文本归一化 | 浏览器中脚本吞掉空格后 ruby 结构不变 | `PlannedText.plannedIndex()` 映射原文→归一化下标 | **100%** | 断言含空格文本中注音仍落在正确字符上 |
| **竖排行间注** | 竖排时注音在基文右侧 | 未实现 | **待后续** | — |
| **竖排** | `writing-mode: vertical-rl` | `KhetiVerticalEngine` + `KhetiVerticalText` | **规则级 100%**（Compose 零支持，全部自研） | 真机+桌面：列序右→左、列距 = 行高网格、每句一列、悬挂到列尾之外 |
| 竖排字距 0.125em | `--vertical { letter-spacing: .125em }` | 引擎沿竖轴施加 | **100%** | 断言步进 = 字号 + 0.125em（22.5px @20px） |
| 竖排标点区域规范 | 大陆靠字身框起始侧 / 台湾居中 | `VerticalGlyph.punctuationOffsetEm` 表驱动 | **100%**（近似：基于通用字形设计） | 断言 CN=(+½,−½)em、TW=(+¼,−¼)em；真机像素差异 1144px |
| 竖排挤压方向 | 逻辑属性 `margin-inline-*` 随书写模式换轴 | 同一份 `AdjustmentPlanner` 增量作用到竖轴 | **100%** | 断言 `。《` 在竖排中上移 7.5px（= 0.5em−字距） |
| 字形朝向（正立/旋转/縦中横） | CSS `text-orientation` + 字体 `vert/vrt2` | `VerticalGlyph` 表：汉字正立、拉丁词与括号旋转 90°、2~3 位数字縦中横 | **规则级 100%**（不依赖字体特性，因多数字体缺 `vert`/`vrt2`） | 朝向分类单测 |
| 多栏 | `column-count` | 未实现 | 未排期 | — |

## 已知偏差（有意为之，全部记录在案）

1. **悬挂标点使视觉重心右移半个标点宽**：上游悬挂元素是绝对定位、不参与居中计算，
   因此四个字列对齐而标点挂在列外。kheti 保持同样行为（这是诗词的排版意图：
   字对齐成列、标点悬挂）。若需要"视觉居中"（把标点计入居中），后续加开关。
2. **两端对齐时逐字分段绘制**：为分配字间余量，justify 行会按字切段，理论上损失西文 kerning。
   CJK 主体文本无影响；混排西文长词场景待 Phase 4 用参考几何量化。
3. **简化禁则**：仅实现"行首禁则"（标点不落行首），尚未实现 clreq 的四级禁则与标点压缩优先序。
4. **平台差异仅作度量来源**：我们**不使用**平台的 `letterSpacing`/`justify`/断行几何，
   只取字形 advance 与断行建议；因此两端 letterSpacing 语义不同（Phase 0 实测）不影响结果。
5. **`TextAlign.Justify` 平台不可用**（Android 只拉空格、Skia 只拉表意文字），故 Justify 必须自研——
   这不是偏差，是平台的限制。

## 无法达成的部分（与"100% 像素级"的关系）

| 项 | 结论 |
|---|---|
| 跨平台像素级一致（系统字体） | **不可达**：字体不同（Android 无宋体、iOS 无思源宋） |
| 跨平台像素级一致（打包同一字体） | **可达**：Phase 0 已证两端自绘像素差异 0 |
| 与浏览器赫蹏像素级一致 | **不可达/无定义**：Chrome 不支持 `text-autospace`、Safari 的 `text-spacing-trim` 会二次修剪，赫蹏自身跨浏览器就不同 |
| `hyphens: auto` | 未实现（平台字典依赖），保留 `break-word` 兜底 |
| BiDi `unicode-bidi: isolate` | 未实现（CJK 优先） |
| 可编辑（IME） | **不做**（display-only，与 Android 官方竖排同定位） |
