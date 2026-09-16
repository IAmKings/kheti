/**
 * Phase 4：浏览器参考几何抽取（**对拍尺子**）
 *
 * 用真实上游产物渲染：`heti` npm 包里的 `umd/heti.min.css` + `umd/heti-addon.min.js`，
 * 在**本机已安装的 Chrome**（playwright-core 驱动，不下载浏览器）里跑 `autoSpacing()`，
 * 再用 `Range.getBoundingClientRect()` 逐字符抽出 x/宽度 —— 这就是"赫蹏真实渲染"的几何。
 *
 * 与 `heti-golden.mjs` 的区别：那份是"逐字复制上游正则"的**规则级**金标准；
 * 这份是**浏览器渲染级**参考，用来定量回答"还原了多少"。
 *
 * 字体：页面通过 @font-face 加载 `reference/fonts/lxgw_wenkai_regular.ttf`，
 * 与 Kotlin 侧使用**同一个 TTF**，因此逐字 x 可以直接比较。
 *
 * 用法：node tools/reference-gen/browser-geometry.mjs > reference/browser-geometry.tsv
 */
import { chromium } from 'playwright-core'
import { readFileSync, writeFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import path from 'node:path'

const HERE = path.dirname(fileURLToPath(import.meta.url))
const ROOT = path.resolve(HERE, '../..')
const css = readFileSync(path.join(HERE, 'node_modules/heti/umd/heti.min.css'), 'utf8')
const js = readFileSync(path.join(HERE, 'node_modules/heti/umd/heti-addon.min.js'), 'utf8')
const fontPath = path.join(ROOT, 'reference/fonts/lxgw_wenkai_regular.ttf')

/** 单行夹具：都能在 672px / 16px 内放下，避免断行差异干扰逐字比较 */
const FIXTURES = [
  '李白乘舟将欲行，忽闻岸上踏歌声。',
  '借指纸。《汉书·外戚传下',
  '中文 abc 中文',
  '赫蹏（hètí）是中文排版增强',
  '第3次测试：iPhone 15 Pro 的差异',
  '价格是100元——真的很贵',
  '《赵后传》所谓『赫蹏』者',
]

const WIDTH = 672
const FONT_SIZE = 16

const html = `<!DOCTYPE html><html lang="zh-Hans"><head><meta charset="utf-8">
<style>
@font-face { font-family: 'KhetiRef'; src: url('file://${fontPath}') format('truetype'); }
${css}
html, body { margin: 0; padding: 0; background: #fff; }
#root { width: ${WIDTH}px; max-width: none; font-family: 'KhetiRef' !important;
        font-size: ${FONT_SIZE}px; line-height: 1.5; }
#root p { margin: 0; padding: 0; }
</style></head><body>
<div class="heti" id="root">${FIXTURES.map((t, i) => `<p data-fx="${i}">${t}</p>`).join('')}</div>
<script>${js}</script>
</body></html>`

const tmp = path.join(HERE, '.browser-geometry.html')
writeFileSync(tmp, html)

const browser = await chromium.launch({
  channel: 'chrome',
  args: ['--allow-file-access-from-files', '--force-device-scale-factor=1'],
})
const page = await browser.newPage({ viewport: { width: 1000, height: 1400 } })
await page.goto('file://' + tmp, { waitUntil: 'load' })

// 上游增强脚本：中西文间距 + 标点挤压（与线上用法一致）
await page.evaluate(() => {
  const h = new window.Heti('#root')
  h.autoSpacing()
})
await page.waitForTimeout(400)

const rows = await page.evaluate(() => {
  const out = []
  document.querySelectorAll('#root p').forEach((p) => {
    const base = p.getBoundingClientRect().left
    const chars = []
    const walker = document.createTreeWalker(p, NodeFilter.SHOW_TEXT)
    let n
    while ((n = walker.nextNode())) {
      for (let i = 0; i < n.data.length; i++) {
        const ch = n.data[i]
        if (ch === '\n') continue
        const r = document.createRange()
        r.setStart(n, i)
        r.setEnd(n, i + 1)
        const rect = r.getBoundingClientRect()
        chars.push({
          ch,
          x: +(rect.left - base).toFixed(3),
          w: +rect.width.toFixed(3),
          top: +rect.top.toFixed(3),
        })
      }
    }
    const lines = [...new Set(chars.map((c) => c.top))].sort((a, b) => a - b)
    out.push({
      text: chars.map((c) => c.ch).join(''),
      lineCount: lines.length,
      chars: chars.map((c) => ({ ch: c.ch, x: c.x, w: c.w, line: lines.indexOf(c.top) })),
    })
  })
  return out
})

await browser.close()

const lines = []
for (const r of rows) {
  const chars = r.chars.map((c) => `${c.ch}:${c.x}:${c.w}:${c.line}`).join(',')
  lines.push([r.text, String(r.lineCount), chars].join('\t'))
}
process.stdout.write(lines.join('\n') + '\n')
console.error(`[browser-geometry] 夹具 ${rows.length} 条；行数=${rows.map((r) => r.lineCount).join('/')}`)
