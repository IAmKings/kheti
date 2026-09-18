#!/usr/bin/env python3
"""生成 kheti 示例 App 的 Android 图标（自适应前景 + 旧版全套密度）。

设计：**白文印** —— 整版朱砂底 + 白色「蹏」字（篆刻里的"朱底白字"印面）。
- 赫蹏是古代书写用的小幅缣帛，印章是最贴切的视觉母题；
- 单字在 48dp 启动器上仍可辨认；
- 白字只出现在 66dp 安全区内，任何启动器蒙版都不会裁掉笔画。

字体用系统 Hiragino Mincho ProN（同时覆盖 赫 U+8D6B / 蹏 U+8E4F）。
项目参考字体 lxgw_neozhisong_screen.ttf 是子集、无 蹏 字，故不可用。

用法：python3 tools/appicon/generate.py [输出目录]
默认输出到 sample/src/androidMain/res/。
"""
from __future__ import annotations

import argparse
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

# ---- 设计参数（改这里即可重出全套图标）----
PAPER = (246, 241, 231)      # 纸色（备用，当前未用于底色）
RED = (168, 50, 38)          # 朱砂（印面）
INK = (34, 30, 25)           # 墨色（备用）
CREAM = (255, 250, 240)      # 印章上的字色（暖白，避免纯白的生硬）
GLYPH = "蹏"                 # 主字
FONT = "/System/Library/Fonts/ヒラギノ明朝 ProN.ttc"
FONT_INDEX = 0
GLYPH_RATIO = 0.40           # 白字字号 / 自适应画布（须落在 66dp 安全区内）

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "sample/src/androidMain/res"

BASE = 432                    # 4x 设计基准（108dp × 4）
SAFE_RADIUS = round(66 / 108 * BASE / 2)   # 132px @432 —— 安全区半径

DENSITIES = [("mdpi", 1), ("hdpi", 2), ("xhdpi", 3), ("xxhdpi", 4), ("xxxhdpi", 5)]
ADAPTIVE_CANVAS = 108
LEGACY_SIZE = 48


def glyph_font(canvas: int) -> ImageFont.FreeTypeFont:
    return ImageFont.truetype(FONT, int(canvas * GLYPH_RATIO), index=FONT_INDEX)


def draw_white_glyph(canvas: int) -> Image.Image:
    """自适应前景：暖白「蹏」居中，落在 66dp 安全区内。"""
    img = Image.new("RGBA", (canvas, canvas), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    font = glyph_font(canvas)
    x0, y0, x1, y1 = d.textbbox((0, 0), GLYPH, font=font)
    gw, gh = x1 - x0, y1 - y0
    d.text((canvas / 2 - gw / 2 - x0, canvas / 2 - gh / 2 - y0),
           GLYPH, font=font, fill=CREAM + (255,))
    return img


def max_radius(img: Image.Image) -> float:
    """内容（非透明像素）距画布中心的最大半径，用于安全区自检。"""
    w = img.size[0]
    px = img.load()
    cx = w / 2
    r_max = 0.0
    for x in range(0, w, 2):
        for y in range(0, w, 2):
            if px[x, y][3] > 8:
                r_max = max(r_max, ((x - w / 2) ** 2 + (y - w / 2) ** 2) ** 0.5)
    return r_max


def check_safe_zone(img: Image.Image) -> tuple[bool, float]:
    r = SAFE_RADIUS / BASE * img.size[0]
    return max_radius(img) <= r * 0.98, max_radius(img)


def legacy_tile(canvas: int, round_mask: bool) -> Image.Image:
    """旧版（API<26）启动器图标：整版朱砂 + 白字；方形（圆角）/ 圆形两种。"""
    bg = Image.new("RGBA", (canvas, canvas), RED + (255,))
    fg = draw_white_glyph(canvas)
    out = Image.alpha_composite(bg, fg).convert("RGB")
    mask = Image.new("L", (canvas, canvas), 0)
    if round_mask:
        ImageDraw.Draw(mask).ellipse([0, 0, canvas - 1, canvas - 1], fill=255)
    else:
        ImageDraw.Draw(mask).rounded_rectangle(
            [0, 0, canvas - 1, canvas - 1], radius=int(canvas * 0.18), fill=255
        )
    rgba = Image.new("RGBA", (canvas, canvas))
    rgba.paste(out, (0, 0), mask)
    return rgba


def main(out_root: Path) -> None:
    fg = draw_white_glyph(BASE)
    ok, r = check_safe_zone(fg)
    print(f"安全区检查（66dp 圆内）: {'✓ 通过' if ok else '✗ 越界'}（最大半径 {r:.0f} / {SAFE_RADIUS}）")

    res = Path(out_root)
    for name, scale in DENSITIES:
        d = res / f"drawable-{name}"
        d.mkdir(parents=True, exist_ok=True)
        draw_white_glyph(ADAPTIVE_CANVAS * scale).save(d / "ic_launcher_foreground.png")

        m = res / f"mipmap-{name}"
        m.mkdir(parents=True, exist_ok=True)
        s = LEGACY_SIZE * scale
        legacy_tile(s, round_mask=False).save(m / "ic_launcher.png")
        legacy_tile(s, round_mask=True).save(m / "ic_launcher_round.png")
        print(f"  {name}: foreground {ADAPTIVE_CANVAS*scale}px, legacy {s}px")

    # 桌面端：compose.desktop 的 macOS { iconFile } 要求 **.icns**（jpackage 约束），
    # 用系统 iconutil 从 512px 底图生成。
    import subprocess, tempfile, shutil

    iconset = Path("/tmp/kheti-icon.iconset")
    shutil.rmtree(iconset, ignore_errors=True)
    iconset.mkdir(parents=True)
    base = legacy_tile(1024, round_mask=False)   # 以最大尺寸缩放，保清晰度
    for tag, size in [
        ("icon_16x16", 16), ("icon_16x16@2x", 32),
        ("icon_32x32", 32), ("icon_32x32@2x", 64),
        ("icon_128x128", 128), ("icon_128x128@2x", 256),
        ("icon_256x256", 256), ("icon_256x256@2x", 512),
        ("icon_512x512", 512), ("icon_512x512@2x", 1024),
    ]:
        base.resize((size, size), Image.LANCZOS).save(iconset / f"{tag}.png")
    icns = ROOT / "sample" / "desktop-icon.icns"
    subprocess.run(["iconutil", "-c", "icns", str(iconset), "-o", str(icns)], check=True)
    print(f"  desktop: {icns.name}")


if __name__ == "__main__":
    p = argparse.ArgumentParser()
    p.add_argument("out", nargs="?", default=str(OUT))
    main(Path(p.parse_args().out))
