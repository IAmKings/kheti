#!/usr/bin/env python3
"""
字体覆盖检查：确认打包字体的 cmap 覆盖了实际要用到的字符。

背景（真实缺陷）：仓库内置的霞鹜文楷/新致宋是 ~5.3k 字的**屏幕子集**，
缺少「蹏」（项目名「赫蹏」本身的字）、〔〕、懈、殂 等。缺字会静默回退到系统字体，
表现为同一行里字形风格不一致（混排观感问题），且不会报错。

用法：
    python3 tools/font-coverage/check.py                 # 检查内置样例文本
    python3 tools/font-coverage/check.py 字体.ttf 文本文件
退出码非 0 表示存在缺字。
"""
import struct
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]


def cmap_codepoints(path: Path) -> set[int]:
    """仅用标准库解析 TTF/OTF 的 cmap，返回覆盖的码点集合。"""
    d = path.read_bytes()
    if d[:4] == b"ttcf":
        raise SystemExit(f"{path.name}: 是 TTC 集合字体，请先拆分为单体 TTF")
    num_tables = struct.unpack(">H", d[4:6])[0]
    tables = {}
    for i in range(num_tables):
        off = 12 + i * 16
        tag = d[off:off + 4].decode("latin1")
        o, l = struct.unpack(">II", d[off + 8:off + 16])
        tables[tag] = (o, l)
    if "cmap" not in tables:
        return set()
    co, _ = tables["cmap"]
    n = struct.unpack(">H", d[co + 2:co + 4])[0]
    best = None
    for i in range(n):
        _, _, off = struct.unpack(">HHI", d[co + 4 + i * 8:co + 12 + i * 8])
        sub = co + off
        fmt = struct.unpack(">H", d[sub:sub + 2])[0]
        if fmt in (4, 12) and (best is None or fmt == 12):
            best = (fmt, sub)
    if not best:
        return set()
    fmt, sub = best
    chars: set[int] = set()
    if fmt == 4:
        seg_x2 = struct.unpack(">H", d[sub + 6:sub + 8])[0]
        seg = seg_x2 // 2
        ends = struct.unpack(">%dH" % seg, d[sub + 14:sub + 14 + seg_x2])
        starts = struct.unpack(">%dH" % seg, d[sub + 16 + seg_x2:sub + 16 + 2 * seg_x2])
        for s, e in zip(starts, ends):
            if s == 0xFFFF:
                continue
            chars.update(range(s, min(e, 0xFFFF) + 1))
    else:
        ngroups = struct.unpack(">I", d[sub + 12:sub + 16])[0]
        for i in range(ngroups):
            s, e, _ = struct.unpack(">III", d[sub + 16 + i * 12:sub + 28 + i * 12])
            chars.update(range(s, min(e, 0x10FFFF) + 1))
    return chars


# 示例 App 与实际用到的最小字符集（诗句 + 古文 + 注音示例）
SAMPLE_TEXT = (
    "赠汪伦〔唐〕李白一剪梅红藕香残玉簟秋轻解罗裳独上兰舟云中谁寄锦书来雁字回时月满西楼"
    "花自飘零水自流一种相思两处闲愁此情无计可消除才下眉头却上心头"
    "出师表先帝创业未半而中道崩殂今天下三分益州疲弊此诚危急存亡之秋也然侍卫之臣不懈于内"
    "忠志之士忘身于外者盖追先帝之殊遇欲报之于陛下也诚宜开张圣听以光先帝遗德恢弘志士之气"
    "宫中府中俱为一体陟罚臧否不宜异同若有作奸犯科及为忠善者宜付有司论其刑赏以昭陛下平明之理"
    "赫蹏是专为中文网页内容设计的排版样式增强可为读者带来更好的阅读体验庖丁为文惠君解牛手之所触肩之所倚"
)


def main() -> int:
    if len(sys.argv) >= 3:
        fonts = [Path(sys.argv[1])]
        text = Path(sys.argv[2]).read_text(encoding="utf-8")
    else:
        fonts = sorted((ROOT / "reference" / "fonts").glob("*.ttf"))
        text = SAMPLE_TEXT
    if not fonts:
        print("未找到字体文件", file=sys.stderr)
        return 2

    failed = False
    for f in fonts:
        covered = cmap_codepoints(f)
        missing = sorted({ch for ch in text if ord(ch) not in covered})
        status = "OK" if not missing else "缺字"
        print(f"[{status}] {f.name}: 覆盖 {len(covered)} 码点；样本缺 {len(missing)} 字"
              + (f" → {''.join(missing)}" if missing else ""))
        if missing:
            failed = True
    if failed:
        print("\n提示：缺字会回退到系统字体，造成同一行字形风格不一致。"
              "\n      需换用全量字体，或调整示例文本，或显式接受该回退。")
    return 1 if failed else 0


if __name__ == "__main__":
    raise SystemExit(main())
