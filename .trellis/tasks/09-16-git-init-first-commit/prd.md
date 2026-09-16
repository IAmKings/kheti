# 初始化 Git 版本控制并首次提交

## Goal

kheti 工作区当前**不是 Git 仓库**（`get_context.py` 报 `Root is not a Git repository`），全部源码、文档与渲染证据只存在于磁盘上；Trellis 的 Finish 阶段也要求 commit。本任务建立版本控制并完成首次提交。

## Requirements

- R1 在 `/Users/pauldeman/Documents/ai_workspace/kheti` 执行 `git init`（默认分支用 `main`）。
- R2 编写 `.gitignore`，至少覆盖：
  - Gradle 产物：`.gradle/`、`build/`、`*/build/`
  - Kotlin/Android：`.kotlin/`、`local.properties`、`.idea/`、`*.iml`
  - macOS：`.DS_Store`
  - Node 依赖与临时页：`tools/reference-gen/node_modules/`、`tools/reference-gen/.browser-geometry.html`
- R3 **证据与字体必须入库**（它们是验收依据，不是产物）：`reference/*.png`、`reference/*.tsv`、`reference/fonts/*.ttf`。
- R4 首次提交内容 = 全部源码（4 个 Gradle 模块）+ `docs/`（PRD/ADR/证据/保真度）+ `reference/` + `tools/` + Gradle wrapper；提交信息说明项目与上游关系（赫蹏 MIT 衍生，见 `NOTICE`）。
- R5 确认 `git status` 干净、且**没有**构建产物或 `node_modules` 被纳入。
- R6 记录基线提交号，供后续任务引用。

## Acceptance Criteria

- [ ] AC1 `git rev-parse --is-inside-work-tree` 返回 `true`，当前分支为 `main`
- [ ] AC2 `git status --porcelain` 输出为空（工作区干净）
- [ ] AC3 `git ls-files | grep -E '(^|/)build/|node_modules|\.gradle/'` 无输出（产物未入库）
- [ ] AC4 `git ls-files reference/ | grep -E '\.(png|ttf|tsv)$'` 非空（证据与字体已入库）
- [ ] AC5 `git log --oneline -1` 有提交，且提交信息含上游归属说明
- [ ] AC6 `.gitignore` 已入库且内容覆盖 R2 列表

## Notes

- 轻量任务，PRD-only。
- 字号/字体子集等大文件已在库内（`reference/fonts` 约 4.7MB），无需 LFS；若后续体积增长再评估。
- 与代码内容无关的既有目录（如 `.dsh/`、`.trellis/`）：`.trellis/` 属于流程元数据，建议一并入库以保留任务记录；`.dsh/` 视平台缓存决定是否忽略（执行时确认）。
