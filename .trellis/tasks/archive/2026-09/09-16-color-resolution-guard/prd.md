# 颜色解析守卫测试（明/暗主题）

## Goal

近几轮真机缺陷的根因属于同一类 —— **颜色未显式解析、或解析成了错误的颜色层级**：

1. `KhetiText` 传 `Color.Unspecified` → 绘制层回退平台默认色（黑）→ **深色模式下整片空白**；
2. 示例页原生 `Material3 Text` 同理（`MaterialTheme` 只在 `Surface` 内适配 `LocalContentColor`）；
3. 图注 / 表注 / 小节标签被涂成 `inkSecondary`（#6B6B6B）→ 浅色模式下 12–14sp 文字**过灰难读**，且**偏离上游**（上游 `caption`/`figcaption` 均不改颜色，继承正文色）。

本任务把这三类问题变成**可被测试拦截**的约束。

## Requirements

- R1 新增守卫测试 `KhetiColorResolutionGuardTest`（置于 `kheti-compose` 的 desktopTest），在 **`KhetiLightColors` 与 `KhetiDarkColors` 两种主题下各跑一遍**。
- R2 **不得回退默认色**：对公共组件产出的块规格（`KhetiBlockSpec`）/布局结果，断言其 `color != Color.Unspecified`。覆盖：`KhetiText`、`KhetiPoem`、`KhetiColumns`、`KhetiVerticalText`、`KhetiHeading`、`KhetiBlockquote`、`KhetiPre`、`KhetiTable`、`KhetiFootnotes`、`KhetiFigcaption`。
- R3 **层级正确**：正文类文本（`KhetiText` 默认、图注、表注）必须解析为 `colors.ink`；仅**元信息**（诗词 meta）允许 `inkSecondary`。
- R4 提炼可测的纯函数并复用：`resolveKhetiColor(color, colors)`（`Unspecified → colors.ink`），替换 `KhetiText`/`KhetiVerticalText`/`KhetiColumns` 中重复的解析逻辑。
- R5 两主题下解析结果必须**不同**（防止"硬编码某个主题的颜色"这类退化）。

## Acceptance Criteria

- [ ] AC1 `./gradlew :kheti-compose:desktopTest --tests "*KhetiColorResolutionGuardTest*"` 通过
- [ ] AC2 测试对 `KhetiText`/`KhetiPoem`/`KhetiColumns`/`KhetiVerticalText` 在明暗两主题下均断言 `color != Color.Unspecified`
- [ ] AC3 断言图注与表注使用 `ink`（而非 `inkSecondary`），并在注释中引用上游依据（`_table.scss` 的 `caption`/`figcaption` 无 color 声明）
- [ ] AC4 `resolveKhetiColor` 为公共（或 internal）纯函数，且 `KhetiText`/`KhetiVerticalText`/`KhetiColumns` 三处均已改用它（无重复实现）
- [ ] AC5 明暗两主题的解析值不相等（AC5 失败即说明存在硬编码）
- [ ] AC6 全量 `:kheti-core:desktopTest :kheti-layout:desktopTest :kheti-compose:desktopTest` 全绿（不回归）

## Notes

- 轻量任务；若 R4 的抽取触及较多调用点，可在 `implement.md` 补执行清单再做。
- 与 `NoPlatformLetterSpacingGuardTest` 属同一类"防静默退化"守卫，命名与注释风格保持一致。
- 上游依据（本地 `tools/reference-gen/node_modules/heti/umd/heti.min.css`，网络不可用时用本地包取证）：
  `.heti caption{caption-side:bottom;...;font-size:14px;line-height:24px}`、`.heti figcaption{display:inline-block;...;font-size:14px;text-align:start}` —— 二者**均无 color**。
