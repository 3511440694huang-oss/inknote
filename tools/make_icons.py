#!/usr/bin/env python3
"""墨笺图标：Seedream 原画 → 扁平化 master → 全密度 mipmap + 自适应图标。"""
from PIL import Image, ImageDraw
import os

SRC = "/sdcard/WORK/generated-images/seedream-20261115-022254-2in52p.png"
RES = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "app/src/main/res")
BG = (247, 240, 229)

im = Image.open(SRC).convert("RGB")
w, h = im.size
# 1) 扁平化：把接近纸色的像素统一成纸色，去掉纸张噪点
px = im.load()
for y in range(h):
    for x in range(w):
        r, g, b = px[x, y]
        if (r-BG[0])**2 + (g-BG[1])**2 + (b-BG[2])**2 < 22**2:
            px[x, y] = BG
# 2) 取墨迹包围盒
xs, ys = [], []
for y in range(0, h, 2):
    for x in range(0, w, 2):
        r, g, b = px[x, y]
        if r + g + b < 250:
            xs.append(x); ys.append(y)
x0, y0, x1, y1 = min(xs), min(ys), max(xs), max(ys)
glyph = im.crop((x0, y0, x1 + 1, y1 + 1))
print("glyph", glyph.size)

def master(size=1024, glyph_frac=0.60):
    """正方形纸底，墨迹按高度缩放居中。"""
    canvas = Image.new("RGB", (size, size), BG)
    gw, gh = glyph.size
    scale = (glyph_frac * size) / gh
    nw, nh = max(1, round(gw * scale)), max(1, round(gh * scale))
    g = glyph.resize((nw, nh), Image.LANCZOS)
    canvas.paste(g, ((size - nw)//2, (size - nh)//2))
    return canvas

M = master()
os.makedirs(RES + "/../tools", exist_ok=True)
M.save(os.path.join(os.path.dirname(os.path.abspath(__file__)), "icon-master.png"))

# 3) 传统图标（各密度：48/72/96/144/192 dp）
legacy = {"mdpi":48, "hdpi":72, "xhdpi":96, "xxhdpi":144, "xxxhdpi":192}
for dpi, pxsize in legacy.items():
    d = os.path.join(RES, "mipmap-" + dpi)
    os.makedirs(d, exist_ok=True)
    icon = M.resize((pxsize, pxsize), Image.LANCZOS)
    icon.save(os.path.join(d, "ic_launcher.png"), optimize=True)
    icon.save(os.path.join(d, "ic_launcher_round.png"), optimize=True)

# 4) 自适应图标前景（108dp 画布；墨迹约 60% 居中，落在 66dp 安全区内）
adaptive = {"mdpi":108, "hdpi":162, "xhdpi":216, "xxhdpi":324, "xxxhdpi":432}
for dpi, pxsize in adaptive.items():
    d = os.path.join(RES, "mipmap-" + dpi)
    fg = M.resize((pxsize, pxsize), Image.LANCZOS)
    fg.save(os.path.join(d, "ic_launcher_foreground.png"), optimize=True)

# 5) 自适应图标 XML
anydpi = os.path.join(RES, "mipmap-anydpi-v26")
os.makedirs(anydpi, exist_ok=True)
for name in ("ic_launcher", "ic_launcher_round"):
    with open(os.path.join(anydpi, name + ".xml"), "w") as f:
        f.write('''<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@color/ic_launcher_background" />
    <foreground android:drawable="@mipmap/ic_launcher_foreground" />
</adaptive-icon>
''')

# 6) 预览图（桌面展示用）
preview = Image.new("RGB", (1024, 360), (255, 255, 255))
prev_icon = M.resize((240, 240), Image.LANCZOS)
# 圆角遮罩预览
mask = Image.new("L", (240, 240), 0)
ImageDraw.Draw(mask).rounded_rectangle([0, 0, 239, 239], radius=54, fill=255)
preview.paste(prev_icon, (60, 60), mask)
circle = Image.new("L", (240, 240), 0)
ImageDraw.Draw(circle).ellipse([0, 0, 239, 239], fill=255)
preview.paste(prev_icon, (380, 60), circle)
preview.paste(M.resize((160,160), Image.LANCZOS), (720, 100))
preview.save(os.path.join(os.path.dirname(os.path.abspath(__file__)), "icon-preview.png"))
print("done: mipmaps written to", RES)
