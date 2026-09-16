/**
 * 赫蹏规则「差分金标准」生成器
 *
 * 目的：用**与上游逐字相同的常量与正则**在 Node 里独立实现一遍赫蹏的 5 趟 DOM 替换，
 * 生成 (归一化文本, 逐字 leading/trailing 增量) 金标准，供 Kotlin 端断言等价。
 *
 * 这是"差分测试"：两个独立实现（JS / Kotlin）在同一份规范下必须给出相同结果。
 * 若需更强证据（直接跑上游未修改代码），Phase 4 可升级为 jsdom + npm 包 `heti`。
 *
 * 上游：https://github.com/sivan/heti （MIT, Copyright (c) 2020 Sivan）
 * 本文件的正则与字符类**逐字复制**自 js/heti-addon.js。
 */

// ---- 上游常量（逐字复制自 js/heti-addon.js）----
const CJK = '\u2e80-\u2eff\u2f00-\u2fdf\u3040-\u309f\u30a0-\u30fa\u30fc-\u30ff\u3100-\u312f\u3200-\u32ff\u3400-\u4dbf\u4e00-\u9fff\uf900-\ufaff'
const A = 'A-Za-z\u0080-\u00ff\u0370-\u03ff'
const N = '0-9'
const S = '`~!@#\\$%\\^&\\*\\(\\)-_=\\+\\[\\]{}\\\\\\|;:\'",<.>\\/\\?'
const ANS = `${A}${N}${S}`
const REG_CJK_FULL = `(?<=[${CJK}])( *[${ANS}]+(?: +[${ANS}]+)* *)(?=[${CJK}])`
const REG_CJK_START = `([${ANS}]+(?: +[${ANS}]+)* *)(?=[${CJK}])`
const REG_CJK_END = `(?<=[${CJK}])( *[${ANS}]+(?: +[${ANS}]+)*)`
const REG_BD_STOP = `。．，、：；！‼？⁇`
const REG_BD_SEP = `·・‧`
const REG_BD_OPEN = `「『（《〈【〖〔［｛`
const REG_BD_CLOSE = `」』）》〉】〗〕］｝`
const REG_BD_START = `${REG_BD_OPEN}${REG_BD_CLOSE}`
const REG_BD_END = `${REG_BD_STOP}${REG_BD_OPEN}${REG_BD_CLOSE}`
const REG_BD_HALF_OPEN = `“‘`
const REG_BD_HALF_CLOSE = `”’`
const REG_BD_HALF_START = `${REG_BD_HALF_OPEN}${REG_BD_HALF_CLOSE}`

// 上游类名 → 增量语义（lib/helpers/_add-on.scss）
const DELTA = {
  'heti-spacing-start': { lead: 0, trail: 0.25 },  // margin-inline-end: .25em
  'heti-spacing-end': { lead: 0.25, trail: 0 },    // margin-inline-start: .25em
  'heti-adjacent-half': { lead: 0, trail: -0.5 },  // margin-inline-end: -.5em
  'heti-adjacent-quarter': { lead: 0, trail: -0.25 }, // margin-inline-end: -.25em
}

/** 极简 DOM：token 列表，每个 token 是纯文本 + 包裹类名列表。 */
function makeTokens(text) {
  return [{ text, classes: [] }]
}

function plain(tokens) {
  return tokens.map((t) => t.text).join('')
}

/** 在 token 列表上做一次"查找替换为包裹元素"，语义等价于上游 Finder。 */
function findAndWrap(tokens, regex, classes) {
  const next = []
  for (const token of tokens) {
    if (token.classes.length > 0) {
      // 已被包裹的内容不再被后续趟次修改（等价上游 HETI_SKIPPED_ELEMENTS）
      next.push(token)
      continue
    }
    let last = 0
    let m
    regex.lastIndex = 0
    while ((m = regex.exec(token.text)) !== null) {
      const groupIndex = m.slice(1).findIndex((g) => g !== undefined)
      if (groupIndex < 0) continue
      const groupText = m[groupIndex + 1]
      const start = m.index + m[0].indexOf(groupText)
      const end = start + groupText.length
      if (start > last) next.push({ text: token.text.slice(last, start), classes: [] })
      next.push({ text: groupText.trim(), classes, raw: groupText })
      last = end
      if (m.index === regex.lastIndex) regex.lastIndex++
    }
    if (last < token.text.length) next.push({ text: token.text.slice(last), classes: [] })
  }
  return next
}

/** 上游 Heti.spacingElement 的 5 趟顺序。 */
function runHeti(text) {
  let tokens = makeTokens(text)
  tokens = findAndWrap(tokens, new RegExp(REG_CJK_FULL, 'g'), ['heti-spacing-start', 'heti-spacing-end'])
  tokens = findAndWrap(tokens, new RegExp(REG_CJK_START, 'g'), ['heti-spacing-start'])
  tokens = findAndWrap(tokens, new RegExp(REG_CJK_END, 'g'), ['heti-spacing-end'])
  tokens = findAndWrap(
    tokens,
    new RegExp(`([${REG_BD_STOP}])(?=[${REG_BD_START}])|([${REG_BD_OPEN}])(?=[${REG_BD_OPEN}])|([${REG_BD_CLOSE}])(?=[${REG_BD_END}])`, 'g'),
    ['heti-adjacent-half'],
  )
  tokens = findAndWrap(
    tokens,
    new RegExp(`([${REG_BD_SEP}])(?=[${REG_BD_OPEN}])|([${REG_BD_CLOSE}])(?=[${REG_BD_SEP}])`, 'g'),
    ['heti-adjacent-quarter'],
  )
  tokens = findAndWrap(
    tokens,
    new RegExp(`([${REG_BD_STOP}])(?=[${REG_BD_HALF_START}])|([${REG_BD_HALF_OPEN}])(?=[${REG_BD_OPEN}])`, 'g'),
    ['heti-adjacent-quarter'],
  )
  return tokens
}

/** 从 token 列表导出 (文本, 逐字 leading, 逐字 trailing)。 */
function toGeometry(tokens) {
  let out = ''
  const lead = []
  const trail = []
  for (const token of tokens) {
    const t = token.text
    if (t.length === 0) continue
    let l = 0
    let r = 0
    for (const cls of token.classes) {
      const d = DELTA[cls]
      l += d.lead
      r += d.trail
    }
    for (let i = 0; i < t.length; i++) {
      out += t[i]
      lead.push(i === 0 ? l : 0)
      trail.push(i === t.length - 1 ? r : 0)
    }
  }
  return { text: out, lead, trail }
}

// ---- 夹具：覆盖赫蹏 5 趟规则 + 官方示例 ----
const FIXTURES = [
  '李白乘舟将欲行，忽闻岸上踏歌声。',
  '独在异乡为异客，',
  '借指纸。《汉书·外戚传下·孝成赵皇后》：「武发篋中，有裹药二枚，赫蹏书。」',
  '《〈【连排',
  '窃·《格瓦拉',
  '字‧《作品',
  '他说。“好”',
  '“《赵后传》',
  '中文 abc 中文',
  'abc 中文',
  '中文 abc',
  '中文 Hello, world! 中文',
  'Hello, world!是大家第一次学习Programming时最常写的demo，它看似简单，但对有些人来说寥寥数语有时也会产生bug。',
  '红藕香残玉簟秋。轻解罗裳，独上兰舟。',
  '云中谁寄锦书来，雁字回时，月满西楼。',
  '抖音App日活破6亿（2024年）。',
  '第3次测试：iPhone 15 Pro Max 与 iPhone 15 Pro 的差异。',
  '（一）《诗经》曰：「关关雎鸠，在河之洲。」',
  '价格是100元——真的很贵……',
  'A股、H股、N股：三者不同。',
  '日本語のテキストと中文混排test。',
]

const rows = []
for (const text of FIXTURES) {
  const tokens = runHeti(text)
  const geo = toGeometry(tokens)
  const removed = text.length - geo.text.length
  // 5 列：原文 \t 归一化文本 \t leading \t trailing \t 吞掉空格数
  rows.push([text, geo.text, geo.lead.join(','), geo.trail.join(','), String(removed)].join('\t'))
}

// 输出 TSV：原文 \t 归一化文本 \t leading \t trailing \t removedSpaces
process.stdout.write(rows.join('\n') + '\n')
