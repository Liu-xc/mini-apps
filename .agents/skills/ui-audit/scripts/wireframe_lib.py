#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""wireframe_lib.py — 灰盒改版线框图绘制库（PIL）。

画布 720x1600（与 1080x2400 截图同比例 0.45）。变更点用蓝色圆徽标①②③，
编号必须与报告侧栏要点一一对应。

铁律：符号自绘。CJK 字体没有 ✓ ‹ › ▸ ▾ ⌖ ✎ 及 emoji 的字形，
写字符会渲染成叉框/tofu——一律用 sym_* 函数。

用法示例见底部 demo()：python3 wireframe_lib.py   → 生成 /tmp/wf_demo.png
依赖: pillow（画图字体自动探测 PingFang/Hiragino/STHeiti）
"""
import math
import os

from PIL import Image, ImageDraw, ImageFont

W, H = 720, 1600

INK = (60, 60, 67)        # 主文字
INK2 = (130, 130, 138)    # 次文字
LINE = (205, 205, 210)    # 常规描边
FILL = (242, 242, 245)    # 容器底
FILL2 = (228, 228, 232)   # 深一档容器
PRIMARY = (74, 74, 80)    # 主按钮（深灰）
ACCENT = (47, 107, 255)   # 变更点标注蓝
ACC_BG = (232, 240, 255)  # 标注蓝淡底
DASH = (170, 170, 178)    # 虚线

_FONT_CANDIDATES = [
    ("/System/Library/Fonts/PingFang.ttc", 0),
    ("/System/Library/Fonts/Hiragino Sans GB.ttc", 0),
    ("/System/Library/Fonts/Hiragino Sans GB.ttc", 2),  # W6 加粗
    ("/System/Library/Fonts/STHeiti Medium.ttc", 1),
]


def _find_font(bold):
    for path, idx in _FONT_CANDIDATES:
        if not os.path.exists(path):
            continue
        try:
            f = ImageFont.truetype(path, 24, index=idx)
            name = f.getname()
            if bold and ("W6" in name or "Medium" in name or "Semibold" in name.title()):
                return path, idx
            if not bold:
                return path, idx
        except Exception:
            continue
    raise RuntimeError("找不到可用 CJK 字体（PingFang/Hiragino/STHeiti）")


def font(sz, bold=False):
    path, idx = _find_font(bold)
    if bold and idx == 0:
        for p, i in _FONT_CANDIDATES[1:]:
            if os.path.exists(p) and i == 2:
                path, idx = p, i
                break
    return ImageFont.truetype(path, sz, index=idx)


def new_canvas(w=W, h=H):
    img = Image.new("RGB", (w, h), (255, 255, 255))
    return img, ImageDraw.Draw(img)


# ---------- 基础 ----------
def rr(d, box, r=14, fill=None, outline=None, width=2):
    d.rounded_rectangle(box, radius=r, fill=fill, outline=outline, width=width)


def txt(d, x, y, s, sz=26, bold=False, fill=INK, anchor="la"):
    d.text((x, y), s, font=font(sz, bold), fill=fill, anchor=anchor)


def badge(d, cx, cy, n):
    """蓝色圆徽标①②③。放置规则：不压文字、不贴画布边缘（留≥20px）。"""
    r = 19
    d.ellipse([cx - r, cy - r, cx + r, cy + r], fill=ACCENT)
    d.text((cx, cy), str(n), font=font(22, True), fill=(255, 255, 255), anchor="mm")


def note(d, x, y, s, sz=21, anchor="la", color=ACCENT):
    d.text((x, y), s, font=font(sz), fill=color, anchor=anchor)


# ---------- 自绘符号（替代缺字形字符） ----------
def sym_check(d, cx, cy, sz=22, color=None):
    c = color or INK
    w = max(3, sz // 6)
    d.line([cx - sz * 0.5, cy + sz * 0.02, cx - sz * 0.15, cy + sz * 0.38], fill=c, width=w)
    d.line([cx - sz * 0.15, cy + sz * 0.38, cx + sz * 0.52, cy - sz * 0.34], fill=c, width=w)


def sym_chev(d, cx, cy, sz=20, left=True, color=None):
    c = color or INK
    w = max(3, sz // 5)
    s = -1 if left else 1
    d.line([(cx + s * sz * 0.3, cy - sz * 0.5), (cx - s * sz * 0.3, cy)], fill=c, width=w)
    d.line([(cx - s * sz * 0.3, cy), (cx + s * sz * 0.3, cy + sz * 0.5)], fill=c, width=w)


def sym_triangle_down(d, cx, cy, sz=10):
    d.polygon([(cx - sz, cy - sz * 0.6), (cx + sz, cy - sz * 0.6), (cx, cy + sz * 0.7)], fill=INK2)


def sym_crosshair(d, cx, cy, r=20):
    d.ellipse([cx - r, cy - r, cx + r, cy + r], outline=INK, width=4)
    d.line([cx - r - 8, cy, cx + r + 8, cy], fill=ACCENT, width=4)
    d.line([cx, cy - r - 8, cx, cy + r + 8], fill=ACCENT, width=4)


def sym_magnifier(d, cx, cy, r=14):
    d.ellipse([cx - r, cy - r, cx + r, cy + r], outline=INK, width=4)
    d.line([(cx + r * 0.7, cy + r * 0.7), (cx + r * 1.6, cy + r * 1.6)], fill=INK, width=5)


def sym_arrow_ne(d, cx, cy, sz=16):
    d.line([(cx - sz, cy + sz), (cx + sz * 0.8, cy - sz * 0.8)], fill=INK, width=4)
    d.line([(cx + sz * 0.8, cy - sz * 0.8), (cx - sz * 0.2, cy - sz * 0.8)], fill=INK, width=4)
    d.line([(cx + sz * 0.8, cy - sz * 0.8), (cx + sz * 0.8, cy + sz * 0.2)], fill=INK, width=4)


def sym_camera(d, cx, cy, w=34):
    d.rounded_rectangle([cx - w / 2, cy - w / 4, cx + w / 2, cy + w / 3], radius=6, outline=INK2, width=4)
    d.ellipse([cx - 9, cy - 6, cx + 9, cy + 12], outline=INK2, width=4)


def sym_person(d, cx, cy, r=13):
    d.ellipse([cx - r * 0.55, cy - r, cx + r * 0.55, cy + r * 0.1], fill=INK2)
    d.ellipse([cx - r, cy + r * 0.25, cx + r, cy + r * 1.9], fill=INK2)


def sym_x(d, cx, cy, sz=12):
    d.line([cx - sz, cy - sz, cx + sz, cy + sz], fill=INK2, width=4)
    d.line([cx + sz, cy - sz, cx - sz, cy + sz], fill=INK2, width=4)


def sym_star_row(d, x, y, filled=4, total=5, sz=22, gap=34):
    for i in range(total):
        cx, cy = x + i * gap + sz, y + sz
        pts = []
        for k in range(10):
            ang = -math.pi / 2 + k * math.pi / 5
            rad = sz if k % 2 == 0 else sz * 0.42
            pts.append((cx + rad * math.cos(ang), cy + rad * math.sin(ang)))
        if i < filled:
            d.polygon(pts, fill=(255, 176, 32))
        else:
            d.polygon(pts, outline=(190, 190, 196))


# ---------- 组件 ----------
def chip(d, x, y, label, w=None, h=52, selected=False, check=False, sz=24):
    """筛选 pill。check=True 时左侧自绘勾。返回右边界 x。"""
    f = font(sz)
    tw = d.textlength(label, font=f)
    pad = 22
    lead = 30 if check else 0
    w = w or (tw + pad * 2 + lead)
    rr(d, [x, y, x + w, y + h], r=h // 2, fill=(226, 228, 235) if selected else None,
       outline=LINE, width=2)
    tx = x + pad
    if check:
        sym_check(d, tx + 8, y + h / 2, sz=17, color=INK if selected else INK2)
        tx += lead
    d.text((tx, y + h / 2), label, font=f, fill=INK if selected else INK2, anchor="lm")
    return x + w


def btn(d, x, y, w, h, label, primary=True, sz=26, check=False, chev=None):
    """按钮。primary=深灰实心；check/chev 自绘前缀符号。"""
    if primary:
        rr(d, [x, y, x + w, y + h], r=h // 2, fill=PRIMARY)
        col = (255, 255, 255)
    else:
        rr(d, [x, y, x + w, y + h], r=h // 2, outline=(150, 150, 156), width=2)
        col = INK
    tw = d.textlength(label, font=font(sz, True))
    tx = x + w / 2 - tw / 2 - (14 if check else 0) - (14 if chev == "left" else 0)
    ty = y + h / 2
    if check:
        sym_check(d, tx + 8, ty, sz=18, color=col)
        tx += 28
    if chev == "left":
        sym_chev(d, tx + 8, ty, sz=15, left=True, color=col)
        tx += 28
    d.text((tx, ty), label, font=font(sz, True), fill=col, anchor="lm")


def imgph(d, box, label="", cross=True, r=10):
    """照片占位：灰底 + 对角叉。"""
    rr(d, box, r=r, fill=(247, 247, 249), outline=LINE, width=2)
    x0, y0, x1, y1 = box
    if cross:
        m = 14
        d.line([x0 + m, y0 + m, x1 - m, y1 - m], fill=(215, 215, 220), width=3)
        d.line([x1 - m, y0 + m, x0 + m, y1 - m], fill=(215, 215, 220), width=3)
    if label:
        d.text(((x0 + x1) / 2, (y0 + y1) / 2 + 16), label, font=font(20), fill=INK2, anchor="mm")


def dashrect(d, box, label="", label_color=INK2, sz=21):
    """虚线框：空槽 / 未就绪 / 占位区。"""
    x0, y0, x1, y1 = box
    step, seg = 14, 8
    x, y = x0, y0
    while x < x1:
        d.line([x, y, min(x + seg, x1), y], fill=DASH, width=3)
        x += step
    x = x0
    y = y1
    while x < x1:
        d.line([x, y, min(x + seg, x1), y], fill=DASH, width=3)
        x += step
    x, y = x0, y0
    while y < y1:
        d.line([x, y, x, min(y + seg, y1)], fill=DASH, width=3)
        y += step
    x, y = x1, y0
    while y < y1:
        d.line([x, y, x, min(y + seg, y1)], fill=DASH, width=3)
        y += step
    if label:
        d.text(((x0 + x1) / 2, (y0 + y1) / 2), label, font=font(sz), fill=label_color, anchor="mm")


def bottom_nav(d, labels, active=0):
    """三 Tab 底部导航。labels 如 ["首页","地图","列表"]。"""
    y = H - 108
    d.line([0, y, W, y], fill=LINE, width=2)
    d.rectangle([0, y, W, H], fill=(250, 250, 252))
    for i, lb in enumerate(labels):
        cx = W * (i + 0.5) / len(labels)
        col = INK if i == active else INK2
        rr(d, [cx - 15, y + 16, cx + 15, y + 46], r=8,
           fill=FILL2 if i == active else None, outline=LINE, width=2)
        d.text((cx, y + 70), lb, font=font(19), fill=col, anchor="mm")


def pager_pill(d, cx, cy, idx, total, w=200, h=56):
    """‹ n/m › 分页胶囊（自绘箭头）。"""
    rr(d, [cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2], r=h // 2, fill=FILL, outline=LINE, width=2)
    sym_chev(d, cx - w / 2 + 34, cy, sz=15, left=True, color=INK2)
    sym_chev(d, cx + w / 2 - 34, cy, sz=15, left=False, color=INK2)
    d.text((cx, cy), f"{idx} / {total}", font=font(24, True), fill=INK, anchor="mm")


def demo(out="/tmp/wf_demo.png"):
    """最小示例：一张带徽标/组件/自绘符号的样例线框。"""
    img, d = new_canvas()
    txt(d, 36, 44, "页面标题", 40, True)
    x = 36
    x = chip(d, x, 116, "选项A", selected=True, check=True) + 12
    chip(d, x, 116, "选项B")
    badge(d, W - 40, 142, 1)
    imgph(d, [96, 210, 624, 640], "照片 hero")
    badge(d, 630, 210, 2)
    btn(d, 36, 680, 330, 84, "主按钮", sz=27)
    btn(d, W - 36 - 240, 680, 240, 84, "次按钮", primary=False, sz=27, check=True)
    pager_pill(d, W / 2, 820, 3, 12)
    dashrect(d, [36, 880, W - 36, 1020], label="空槽 / 占位")
    sym_star_row(d, 60, 1060, filled=4)
    sym_crosshair(d, 80, 1200)
    sym_magnifier(d, 180, 1200)
    sym_camera(d, 280, 1200)
    sym_person(d, 380, 1195)
    sym_arrow_ne(d, 470, 1200)
    note(d, 36, 1300, "蓝色注释：改版意图说明", 20)
    badge(d, 320, 1310, 3)
    bottom_nav(d, ["首页", "地图", "列表"], active=0)
    img.save(out)
    print("demo saved:", out)
    return out


if __name__ == "__main__":
    demo()
