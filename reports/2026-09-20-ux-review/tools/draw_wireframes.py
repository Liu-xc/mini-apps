# -*- coding: utf-8 -*-
"""UX 评审报告 · 改版线框图绘制（灰盒风格 + 蓝色变更点标注）· v2
v2: 符号安全化——Hiragino Sans GB 缺 ✓ ‹› ⌖ ✎ 及全部 emoji 字形，
    一律改为自绘矢量符号或纯文字；新增 wf-15 吃啥表单；修徽章遮挡。
输出: assets/wf-*.png, 720x1600
"""
from PIL import Image, ImageDraw, ImageFont
import os

W, H = 720, 1600
OUT = os.path.normpath(os.path.join(os.path.dirname(__file__), "..", "assets"))

INK      = (60, 60, 67)
INK2     = (130, 130, 138)
LINE     = (205, 205, 210)
FILL     = (242, 242, 245)
FILL2    = (228, 228, 232)
PRIMARY  = (74, 74, 80)
ACCENT   = (47, 107, 255)
ACC_BG   = (232, 240, 255)
DASH     = (170, 170, 178)
GOOD     = (60, 60, 67)

F_REG  = "/System/Library/Fonts/Hiragino Sans GB.ttc"
F_MED  = 2  # W6 index

def font(sz, bold=False):
    if bold:
        return ImageFont.truetype(F_REG, sz, index=F_MED)
    return ImageFont.truetype(F_REG, sz)

def new_canvas():
    img = Image.new("RGB", (W, H), (255, 255, 255))
    return img, ImageDraw.Draw(img)

def rr(d, box, r=14, fill=None, outline=None, width=2):
    d.rounded_rectangle(box, radius=r, fill=fill, outline=outline, width=width)

def txt(d, x, y, s, sz=26, bold=False, fill=INK, anchor="la"):
    d.text((x, y), s, font=font(sz, bold), fill=fill, anchor=anchor)

def badge(d, cx, cy, n):
    r = 19
    d.ellipse([cx - r, cy - r, cx + r, cy + r], fill=ACCENT)
    d.text((cx, cy), str(n), font=font(22, True), fill=(255, 255, 255), anchor="mm")

def note(d, x, y, s, sz=21, anchor="la", color=ACCENT):
    d.text((x, y), s, font=font(sz), fill=color, anchor=anchor)

# ---------- 自绘符号（替代缺字形字符） ----------
def sym_check(d, cx, cy, sz=22, color=None):
    c = color or GOOD
    w = 4
    d.line([cx - sz*0.5, cy + sz*0.02, cx - sz*0.15, cy + sz*0.38], fill=c, width=w)
    d.line([cx - sz*0.15, cy + sz*0.38, cx + sz*0.52, cy - sz*0.34], fill=c, width=w)

def sym_chev(d, cx, cy, sz=20, left=True, color=None):
    c = color or GOOD
    w = 4
    if left:
        d.line([cx + sz*0.3, cy - sz*0.5, cx - sz*0.3, cy], fill=c, width=w)
        d.line([cx - sz*0.3, cy, cx + sz*0.3, cy + sz*0.5], fill=c, width=w)
    else:
        d.line([cx - sz*0.3, cy - sz*0.5, cx + sz*0.3, cy], fill=c, width=w)
        d.line([cx + sz*0.3, cy, cx - sz*0.3, cy + sz*0.5], fill=c, width=w)

def sym_crosshair(d, cx, cy, r=20):
    d.ellipse([cx - r, cy - r, cx + r, cy + r], outline=INK, width=4)
    d.line([cx - r - 8, cy, cx + r + 8, cy], fill=ACCENT, width=4)
    d.line([cx, cy - r - 8, cx, cy + r + 8], fill=ACCENT, width=4)

def sym_magnifier(d, cx, cy, r=14):
    d.ellipse([cx - r, cy - r, cx + r, cy + r], outline=INK, width=4)
    d.line([cx + r*0.7, cy + r*0.7, cx + r*1.6, cy + r*1.6], fill=INK, width=5)

def sym_triangle_down(d, cx, cy, sz=10):
    d.polygon([(cx - sz, cy - sz*0.6), (cx + sz, cy - sz*0.6), (cx, cy + sz*0.7)], fill=INK2)

def sym_arrow_ne(d, cx, cy, sz=16):
    d.line([(cx - sz, cy + sz), (cx + sz*0.8, cy - sz*0.8)], fill=INK, width=4)
    d.line([(cx + sz*0.8, cy - sz*0.8), (cx - sz*0.2, cy - sz*0.8)], fill=INK, width=4)
    d.line([(cx + sz*0.8, cy - sz*0.8), (cx + sz*0.8, cy + sz*0.2)], fill=INK, width=4)

def sym_camera(d, cx, cy, w=34):
    d.rounded_rectangle([cx - w/2, cy - w/4, cx + w/2, cy + w/3], radius=6, outline=INK2, width=4)
    d.ellipse([cx - 9, cy - 6, cx + 9, cy + 12], outline=INK2, width=4)

def sym_person(d, cx, cy, r=13):
    d.ellipse([cx - r*0.55, cy - r, cx + r*0.55, cy + r*0.1], fill=INK2)
    d.ellipse([cx - r, cy + r*0.25, cx + r, cy + r*1.9], fill=INK2)

def sym_x(d, cx, cy, sz=12):
    d.line([cx - sz, cy - sz, cx + sz, cy + sz], fill=INK2, width=4)
    d.line([cx + sz, cy - sz, cx - sz, cy + sz], fill=INK2, width=4)

def sym_star_row(d, x, y, filled=4, total=5, sz=22, gap=34):
    for i in range(total):
        cx, cy = x + i * gap + sz, y + sz
        pts = []
        import math
        for k in range(10):
            ang = -math.pi/2 + k * math.pi/5
            rad = sz if k % 2 == 0 else sz * 0.42
            pts.append((cx + rad * math.cos(ang), cy + rad * math.sin(ang)))
        if i < filled:
            d.polygon(pts, fill=(255, 176, 32))
        else:
            d.polygon(pts, outline=(190, 190, 196))

def chip(d, x, y, label, w=None, h=52, selected=False, check=False, sz=24):
    f = font(sz)
    tw = d.textlength(label, font=f)
    pad = 22
    lead = (30 if check else 0)
    w = w or (tw + pad * 2 + lead)
    fill = (226, 228, 235) if selected else None
    rr(d, [x, y, x + w, y + h], r=h // 2, fill=fill, outline=LINE, width=2)
    tx = x + pad
    if check:
        sym_check(d, tx + 8, y + h / 2, sz=17, color=INK if selected else INK2)
        tx += lead
    d.text((tx, y + h / 2), label, font=f, fill=INK if selected else INK2, anchor="lm")
    return x + w

def btn(d, x, y, w, h, label, primary=True, sz=26, check=False, chev=None):
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
    rr(d, box, r=r, fill=(247, 247, 249), outline=LINE, width=2)
    x0, y0, x1, y1 = box
    if cross:
        m = 14
        d.line([x0 + m, y0 + m, x1 - m, y1 - m], fill=(215, 215, 220), width=3)
        d.line([x1 - m, y0 + m, x0 + m, y1 - m], fill=(215, 215, 220), width=3)
    if label:
        d.text(((x0 + x1) / 2, (y0 + y1) / 2 + 16), label, font=font(20), fill=INK2, anchor="mm")

def dashrect(d, box, label="", label_color=INK2, sz=21):
    x0, y0, x1, y1 = box
    step, seg = 14, 8
    x, y = x0, y0
    while x < x1:
        d.line([x, y, min(x + seg, x1), y], fill=DASH, width=3); x += step
    x = x0; y = y1
    while x < x1:
        d.line([x, y, min(x + seg, x1), y], fill=DASH, width=3); x += step
    x, y = x0, y0
    while y < y1:
        d.line([x, y, x, min(y + seg, y1)], fill=DASH, width=3); y += step
    x = x1; y = y0
    while y < y1:
        d.line([x, y, x, min(y + seg, y1)], fill=DASH, width=3); y += step
    if label:
        d.text(((x0 + x1) / 2, (y0 + y1) / 2), label, font=font(sz), fill=label_color, anchor="mm")

def bottom_nav(d, active=0):
    y = H - 108
    d.line([0, y, W, y], fill=LINE, width=2)
    d.rectangle([0, y, W, H], fill=(250, 250, 252))
    labels = ["吃什么", "地图", "列表"] if active < 3 else ["搭配", "穿搭记录", "衣橱"]
    for i, lb in enumerate(labels):
        cx = W * (i + 0.5) / 3
        col = INK if i == (active % 3) else INK2
        rr(d, [cx - 15, y + 16, cx + 15, y + 46], r=8, fill=FILL2 if i == (active % 3) else None, outline=LINE, width=2)
        d.text((cx, y + 70), lb, font=font(19), fill=col, anchor="mm")

def pager_pill(d, cx, cy, idx, total, w=200, h=56):
    rr(d, [cx - w/2, cy - h/2, cx + w/2, cy + h/2], r=h//2, fill=FILL, outline=LINE, width=2)
    sym_chev(d, cx - w/2 + 34, cy, sz=15, left=True, color=INK2)
    sym_chev(d, cx + w/2 - 34, cy, sz=15, left=False, color=INK2)
    d.text((cx, cy), f"{idx} / {total}", font=font(24, True), fill=INK, anchor="mm")

def save(img, name):
    p = os.path.join(OUT, name)
    img.save(p)
    print("saved", name)


# ============================================================
def wf01():
    img, d = new_canvas()
    M = 36
    txt(d, M, 44, "今天吃啥", 40, True)
    x = M; y = 116
    txt(d, x, y + 14, "类型", 24, fill=INK2)
    x += 78
    x = chip(d, x, y, "堂食", selected=True, check=True) + 12
    x = chip(d, x, y, "外卖", selected=True, check=True) + 12
    x = chip(d, x, y, "自做", selected=False) + 12
    chip(d, x, y, "筛选")
    badge(d, W - 40, y + 26, 1)
    note(d, M, y + 66, "忌口/排除最近 收进「筛选」，省一行", 20)

    cx0, cy0, cx1, cy1 = 96, 250, 624, 880
    for off in (26, 13):
        rr(d, [cx0 - off, cy0 - off, cx1 - off, cy1 - off], r=18, fill=(232, 232, 236), outline=LINE, width=2)
    rr(d, [cx0, cy0, cx1, cy1], r=18, fill=(255, 255, 255), outline=(160, 160, 166), width=3)
    imgph(d, [cx0 + 20, cy0 + 20, cx1 - 20, cy0 + 250], "照片 hero")
    txt(d, cx0 + 22, cy0 + 292, "巷子深火锅", 32, True)
    chip(d, cx1 - 118, cy0 + 288, "堂食", h=46, sz=22)
    txt(d, cx0 + 22, cy0 + 344, "★4 · 今天 · 4 次 · 火锅", 24, fill=INK2)
    xx = cx0 + 22
    xx = chip(d, xx, cy0 + 396, "#辣", h=44, sz=21) + 10
    chip(d, xx, cy0 + 396, "#重口味", h=44, sz=21)
    btn(d, cx0 + 22, cy0 + 470, 210, 66, "美团 · 套餐", primary=False, sz=23)
    btn(d, cx0 + 252, cy0 + 470, 230, 66, "点评 · 主页", primary=False, sz=23)
    btn(d, cx0 + 22, cy0 + 560, 180, 64, "＋ 记一笔", sz=24)
    btn(d, cx0 + 220, cy0 + 560, 130, 64, "详情", primary=False, sz=24)
    badge(d, cx1 + 6, cy0 - 6, 2)
    note(d, cx1 - 10, cy0 - 44, "后卡露边 8-12dp 常驻", 20, anchor="ra")

    pager_pill(d, W / 2, 940, 3, 12)
    badge(d, W / 2 + 130, 940, 3)
    note(d, W / 2, 986, "卡序 + 候选数常驻，筛选即反馈剩几家", 20, anchor="ma")

    btn(d, M, 1050, 330, 84, "随机抽一张", sz=27)
    btn(d, W - M - 240, 1050, 240, 84, "换一张", primary=False, sz=27)
    dashrect(d, [M, 1180, W - M, 1350], label="")
    d.text((W / 2, 1226), "抽取落定后，此区域升格为结果确认块", font=font(22), fill=INK2, anchor="mm")
    d.text((W / 2, 1272), "（见下一张线框）", font=font(20), fill=INK2, anchor="mm")
    bottom_nav(d, 0)
    save(img, "wf-01-eats-home.png")

def wf02():
    img, d = new_canvas()
    M = 36
    txt(d, M, 44, "今天吃啥", 40, True)
    x = M; y = 116
    txt(d, x, y + 14, "类型", 24, fill=INK2)
    x += 78
    x = chip(d, x, y, "堂食", selected=True, check=True) + 12
    x = chip(d, x, y, "外卖", selected=True, check=True) + 12
    chip(d, x, y, "自做")

    cx0, cy0, cx1, cy1 = 130, 240, 590, 700
    rr(d, [cx0 - 13, cy0 - 13, cx1 - 13, cy1 - 13], r=16, fill=(232, 232, 236), outline=LINE, width=2)
    rr(d, [cx0, cy0, cx1, cy1], r=16, fill=(255, 255, 255), outline=(160, 160, 166), width=3)
    imgph(d, [cx0 + 18, cy0 + 18, cx1 - 18, cy0 + 200], "抽中卡（自动滚入）")
    txt(d, cx0 + 20, cy0 + 248, "红烧排骨 · 自做", 30, True)
    txt(d, cx0 + 20, cy0 + 306, "上次 5 天前 · 3 次", 24, fill=INK2)
    badge(d, cx1 + 8, cy1 + 44, 3)
    pager_pill(d, W / 2, cy1 + 44, 7, 12, w=170, h=50)
    note(d, W / 2, cy1 + 96, "落定时卡组自动收拢让位，不新增滚动", 20, anchor="ma")

    bx0, by0, bx1, by1 = M, 880, W - M, 1230
    import random
    random.seed(7)
    for _ in range(26):
        px = random.randint(bx0 + 6, bx1 - 6)
        py = random.randint(by0 - 44, by0 + 8)
        c = random.choice([(255, 120, 120), (120, 180, 255), (255, 200, 90), (150, 220, 150)])
        d.ellipse([px, py, px + 9, py + 9], fill=c)
    rr(d, [bx0, by0, bx1, by1], r=22, fill=(52, 52, 58))
    txt(d, W / 2, by0 + 52, "就吃 红烧排骨？", 40, True, fill=(255, 255, 255), anchor="ma")
    txt(d, W / 2, by0 + 122, "抽自 12 家候选 · #家常", 23, fill=(200, 200, 208), anchor="ma")
    btn(d, bx0 + 34, by0 + 180, 290, 88, "就吃这个", primary=True, sz=28, check=True)
    rr(d, [bx0 + 34 + 306, by0 + 180, bx1 - 34, by0 + 268], r=44, outline=(220, 220, 226), width=3)
    d.text(((bx0 + 34 + 306 + bx1 - 34) / 2, by0 + 224), "再抽", font=font(27), fill=(255, 255, 255), anchor="mm")
    badge(d, bx1 - 4, by0 - 8, 1)
    note(d, bx1 - 14, by1 + 20, "强调结果块", 20, anchor="ra")
    badge(d, bx1 - 150, by1 - 4, 2)
    note(d, bx1 - 174, by1 + 20, "按钮常驻屏内", 20, anchor="ra")
    bottom_nav(d, 0)
    save(img, "wf-02-eats-drawn.png")

def wf03():
    img, d = new_canvas()
    M = 36
    txt(d, M, 44, "地图", 40, True)
    ly = 118
    rr(d, [M, ly, M + 430, ly + 62], r=31, fill=(255, 255, 255), outline=LINE, width=2)
    for i, (lb, c) in enumerate([("堂食", (214, 69, 66)), ("外卖", (235, 159, 60)), ("自做", (108, 175, 80))]):
        cx = M + 30 + i * 140
        d.ellipse([cx, ly + 20, cx + 22, ly + 42], fill=c)
        d.text((cx + 32, ly + 31), lb, font=font(23), fill=INK, anchor="lm")
    badge(d, M + 430 + 26, ly + 31, 1)
    rr(d, [W - M - 66, ly - 4, W - M, ly + 62], r=16, fill=FILL, outline=LINE, width=2)
    sym_crosshair(d, W - M - 33, ly + 28, r=16)
    badge(d, W - M - 33, ly - 26, 2)

    mb = [M, 216, W - M, 1180]
    rr(d, mb, r=20, fill=(240, 241, 244), outline=LINE, width=2)
    for yy in range(300, 1150, 130):
        d.line([mb[0] + 24, yy, mb[2] - 24, yy], fill=(228, 229, 233), width=6)
    for xx in range(150, 640, 150):
        d.line([xx, mb[1] + 24, xx, mb[3] - 24], fill=(228, 229, 233), width=6)
    pts = [(170, 340, (214, 69, 66)), (470, 300, (235, 159, 60)), (300, 560, (214, 69, 66)),
           (560, 640, (108, 175, 80)), (220, 830, (235, 159, 60)), (480, 950, (214, 69, 66)), (330, 1090, (108, 175, 80))]
    for (px, py, c) in pts:
        d.ellipse([px - 15, py - 15, px + 15, py + 15], fill=(255, 255, 255), outline=c, width=5)
    sx, sy = 480, 950
    d.ellipse([sx - 26, sy - 26, sx + 26, sy + 26], outline=ACCENT, width=4)
    badge(d, mb[2] - 30, mb[1] + 34, 3)
    note(d, mb[2] - 44, mb[1] + 62, "配色回归 spec：红/琥珀/绿", 20, anchor="ra")

    sb = [M, 1216, W - M, 1360]
    rr(d, sb, r=18, fill=(255, 255, 255), outline=LINE, width=2)
    txt(d, sb[0] + 24, sb[1] + 18, "巷子深火锅", 28, True)
    txt(d, sb[0] + 24, sb[1] + 70, "堂食 · ★4 · 上次 3 天前 · 共 6 次", 23, fill=INK2)
    d.text((sb[2] - 60, sb[1] + 52), "详情", font=font(24), fill=ACCENT, anchor="lm")
    sym_chev(d, sb[2] - 34, sb[1] + 52, sz=13, left=False, color=ACCENT)
    bottom_nav(d, 1)
    save(img, "wf-03-eats-map.png")

def wf04():
    img, d = new_canvas()
    M = 36
    txt(d, M, 44, "食堂 · 9 家", 38, True)
    ty = 120
    rr(d, [W - M - 330, ty, W - M, ty + 58], r=29, fill=FILL, outline=LINE, width=2)
    sym_magnifier(d, W - M - 292, ty + 29, r=12)
    d.text((W - M - 254, ty + 29), "最近", font=font(23), fill=INK, anchor="lm")
    sym_triangle_down(d, W - M - 212, ty + 29, sz=9)
    d.line([W - M - 178, ty + 12, W - M - 178, ty + 46], fill=LINE, width=2)
    d.text((W - M - 156, ty + 29), "筛选 · 2", font=font(23, True), fill=(40, 80, 200), anchor="lm")
    d.ellipse([W - M - 62, ty + 2, W - M - 46, ty + 18], fill=ACCENT)
    badge(d, W - M - 348, ty + 29, 1)
    badge(d, W - M - 54, ty - 18, 3)
    note(d, W / 2, 200, "筛选收进底部弹层，应用后带角标计数", 20, anchor="ma")

    rows_y = 240
    for i in range(5):
        y = rows_y + i * 218
        rr(d, [M, y, W - M, y + 198], r=18, fill=(255, 255, 255), outline=LINE, width=2)
        imgph(d, [M + 18, y + 18, M + 158, y + 180], "图")
        txt(d, M + 186, y + 22, ["巷子深火锅", "猪脚饭", "鮨 · 日料亭", "麦当劳", "番茄炒蛋"][i], 27, True)
        txt(d, M + 186, y + 76, ["堂食 · ★4 · 火锅", "外卖 · ★5 · 快餐", "堂食 · 未评分 · 日料", "外卖 · ★3 · 快餐", "自做 · ★5 · 家常"][i], 22, fill=INK2)
        chip(d, M + 186, y + 122, ["#辣 #重口味", "#一人食", "#清淡", "#一人食 #便宜", "#家常"][i], h=42, sz=20)
        txt(d, W - M - 18, y + 60, ["3 天前", "昨天", "1 周前", "4 天前", "2 天前"][i], 21, fill=INK2, anchor="ra")
    badge(d, M + 20, rows_y + 4 * 218 + 40, 2)
    note(d, M + 48, rows_y + 4 * 218 + 46, "首屏 5-6 张卡", 20)
    d.ellipse([W - M - 92, 1274, W - M, 1366], fill=PRIMARY)
    d.text((W - M - 46, 1320), "＋", font=font(40, True), fill=(255, 255, 255), anchor="mm")
    bottom_nav(d, 2)
    save(img, "wf-04-eats-list.png")

def wf05():
    img, d = new_canvas()
    M = 36
    sym_chev(d, M + 24, 62, sz=17, left=True, color=INK2)
    txt(d, M + 62, 48, "巷子深火锅", 34, True)
    txt(d, W - M - 44, 56, "编辑", 23, fill=INK2, anchor="ra")
    imgph(d, [M, 120, W - M, 470], "店铺照片")
    txt(d, M, 500, "巷子深火锅 · 堂食 · 火锅", 30, True)
    txt(d, M, 558, "均分 4.3 · 共 4 次 · 上次 今天", 24, fill=INK2)
    rr(d, [M, 612, W - M, 672], r=10, fill=FILL)
    txt(d, M + 18, 624, "某某路 12 号", 23)
    d.text((W - M - 96, 642), "在地图上看", font=font(23), fill=ACCENT, anchor="lm")
    sym_arrow_ne(d, W - M - 38, 634, sz=10)
    x = M
    x = chip(d, x, 700, "#辣", h=44, sz=21) + 10
    chip(d, x, 700, "#重口味", h=44, sz=21)
    txt(d, M, 780, "吃过记录 (4)", 27, True)
    for i in range(3):
        y = 836 + i * 128
        rr(d, [M, y, W - M, y + 110], r=14, fill=(255, 255, 255), outline=LINE, width=2)
        txt(d, M + 20, y + 14, ["9/20  ★4  ¥128", "9/03  ★4  ¥99", "8/22  ★5  ¥140"][i], 23, True)
        txt(d, M + 20, y + 62, ["毛肚绝了", "错峰不用排队", "辣度刚好"][i], 22, fill=INK2)
    txt(d, W / 2, 1252, "…（滚动区，记录再多也不挡主操作）", 21, fill=INK2, anchor="ma")
    by = H - 190
    d.rectangle([0, by - 24, W, H], fill=(255, 255, 255))
    d.line([0, by - 24, W, by - 24], fill=LINE, width=2)
    btn(d, M, by, 470, 88, "＋ 记一笔今天吃了", sz=27)
    rr(d, [M + 494, by, W - M, by + 88], r=44, outline=(150, 150, 156), width=2)
    d.text(((M + 494 + W - M) / 2, by + 44), "删除", font=font(25), fill=INK, anchor="mm")
    badge(d, M + 240, by - 40, 1)
    note(d, W / 2, by - 64, "主操作吸底常驻", 20, anchor="ma")
    save(img, "wf-05-eats-detail.png")

def wf06():
    img, d = new_canvas()
    M = 36
    d.rectangle([0, 0, W, 330], fill=(120, 120, 128))
    sy = 330
    d.rectangle([0, sy, W, H], fill=(255, 255, 255))
    rr(d, [W / 2 - 40, sy + 16, W / 2 + 40, sy + 26], r=5, fill=(200, 200, 205))
    txt(d, W / 2, sy + 52, "记一笔 · 巷子深火锅", 30, True, anchor="ma")
    y = sy + 120
    txt(d, M, y + 14, "时间", 24, fill=INK2)
    rr(d, [M + 110, y, W - M, y + 64], r=12, fill=FILL, outline=LINE, width=2)
    txt(d, M + 132, y + 16, "今天 12:30   改", 24)
    y += 92
    txt(d, M, y + 14, "评分", 24, fill=INK2)
    rr(d, [M + 110, y, W - M, y + 64], r=12, fill=FILL, outline=LINE, width=2)
    sym_star_row(d, M + 140, y + 10, filled=4, total=5, sz=17, gap=44)
    y += 92
    txt(d, M, y + 14, "花费", 24, fill=INK2)
    rr(d, [M + 110, y, W - M, y + 64], r=12, fill=FILL, outline=LINE, width=2)
    txt(d, M + 132, y + 16, "¥ 128", 24)
    y += 92
    txt(d, M, y + 14, "感想", 24, fill=INK2)
    rr(d, [M + 110, y, W - M, y + 64], r=12, fill=FILL, outline=LINE, width=2)
    txt(d, M + 132, y + 16, "毛肚绝了", 24)
    y += 96
    rr(d, [M, y + 8, W - M, y + 70], r=12, fill=FILL, outline=LINE, width=2)
    d.text((M + 22, y + 40), "＋ 照片", font=font(24), fill=INK2, anchor="lm")
    by = y + 106
    btn(d, M, by, W - 2 * M, 88, "落 账", sz=30)
    badge(d, W - M - 40, by + 44, 1)
    ky = by + 116
    d.rectangle([0, ky, W, H], fill=(216, 217, 222))
    note(d, W / 2, by + 96, "imePadding：按钮随键盘上移，永不被遮挡", 20, anchor="ma")
    d.text((W / 2, ky - 26), "── 软键盘 ──", font=font(20), fill=(150, 150, 156), anchor="mm")
    for r in range(3):
        kyy = ky + 70 + r * 96
        for c in range(5):
            kx = 44 + c * 132
            rr(d, [kx, kyy, kx + 116, kyy + 76], r=10, fill=(255, 255, 255), outline=(198, 199, 205), width=2)
    save(img, "wf-06-eats-log.png")

def _form_fields(d, title, fields, photo_label):
    """表单公共绘制：字段列表 + 必填照片区 + 吸底两态保存"""
    M = 36
    sym_x(d, M + 22, 62, sz=11)
    txt(d, M + 62, 44, title, 34, True)
    y = 130
    for lb, val in fields:
        txt(d, M, y + 14, lb, 24, fill=INK2)
        rr(d, [M + 128, y, W - M, y + 64], r=12, fill=FILL, outline=LINE, width=2)
        if val:
            txt(d, M + 150, y + 16, val, 23)
        y += 96
    dashrect(d, [M, y + 10, W - M, y + 150])
    sym_camera(d, W / 2 - 52, y + 62, w=32)
    d.text((W / 2 + 6, y + 62), "照片（必填）", font=font(24), fill=INK2, anchor="lm")
    d.text((W / 2, y + 112), "选完照片即可保存", font=font(20), fill=INK2, anchor="mm")
    badge(d, W - M - 20, y + 40, 2)
    txt(d, M, y + 190, "…（长表单滚动区）", 22, fill=INK2)
    by = H - 350
    rr(d, [M, by, W - M, by + 92], r=46, fill=(226, 227, 231))
    d.text((W / 2, by + 46), "选择照片后可保存", font=font(25), fill=(140, 141, 148), anchor="mm")
    txt(d, M, by + 112, "未就绪（原因写清楚）", 20, fill=INK2)
    by2 = H - 190
    btn(d, M, by2, W - 2 * M, 92, "保  存", sz=30)
    txt(d, M, by2 + 104, "就绪后变实心主按钮", 20, fill=INK2)
    badge(d, W - M - 34, by + 46, 1)
    note(d, W - M - 6, by - 46, "吸底操作栏 + imePadding", 20, anchor="ra")

def wf07():
    img, d = new_canvas()
    _form_fields(d, "添加衣物", [
        ("名称 *", "白色牛津纺衬衫"), ("品类 *", "(上装) 外套 下装 …"), ("颜色", "白色"),
        ("描述", "宽松棉质、纽扣领"), ("标签", "(通勤) (简约) ＋"), ("备注", ""),
    ], "")
    save(img, "wf-07-form-save.png")

def wf15():
    img, d = new_canvas()
    _form_fields(d, "添加食堂", [
        ("名称 *", "巷子深火锅"), ("类型 *", "(堂食) 外卖 自做"), ("菜系", "火锅"),
        ("位置", "长按地图选点 …"), ("链接", "粘贴分享链接 ＋"), ("标签", "(辣) (重口味) ＋"),
    ], "")
    save(img, "wf-15-eats-form.png")

def wf08():
    img, d = new_canvas()
    M = 36
    txt(d, M, 44, "Leo", 32, True)
    sym_triangle_down(d, M + 92, 62, sz=10)
    txt(d, W - M, 48, "随机一套", 24, fill=ACCENT, anchor="ra")

    def slot(box, cat, idx, total, ph_label="照片", pager=True):
        x0, y0, x1, y1 = box
        rr(d, box, r=14, fill=(255, 255, 255), outline=(160, 160, 166), width=3)
        imgph(d, [x0 + 12, y0 + 12, x1 - 12, y1 - 54], ph_label, r=8)
        txt(d, x0 + 16, y1 - 44, cat, 21, fill=INK2)
        if pager:
            pw = 128
            px1 = x1 - 14
            rr(d, [px1 - pw, y1 - 52, px1, y1 - 6], r=23, fill=FILL2)
            sym_chev(d, px1 - pw + 26, y1 - 29, sz=12, left=True, color=INK)
            sym_chev(d, px1 - 26, y1 - 29, sz=12, left=False, color=INK)
            d.text((px1 - pw / 2, y1 - 29), f"{idx}/{total}", font=font(20, True), fill=INK, anchor="mm")

    slot([250, 120, 470, 300], "帽子", 1, 2)
    ry0, ry1 = 318, 640
    slot([M, ry0, 168, ry1], "包", 1, 2)
    slot([180, ry0, 330, ry1], "外套", 2, 2)
    slot([342, ry0, 492, ry1], "上装", 2, 3)
    slot([504, ry0, 640, ry1], "连衣裙", 1, 2)
    slot([180, 658, 640, 1000], "下装", 1, 2, "照片（长卡）")
    slot([250, 1018, 470, 1160], "鞋", 1, 1, "照片", pager=True)
    badge(d, 330, 300, 1)
    note(d, W / 2, 1166, "序号胶囊加翻页箭头，明示可滑", 19, anchor="ma")
    badge(d, 150, 1218, 2)
    note(d, 182, 1208, "首次进入每格做左右微移示意（仅首轮）", 19)
    by = 1250
    btn(d, M, by, 340, 84, "复制长图", sz=26)
    rr(d, [W - M - 260, by, W - M, by + 84], r=42, outline=(150, 150, 156), width=2)
    d.text((W - M - 130, by + 42), "☆ 保存这套", font=font(25), fill=INK, anchor="mm")
    badge(d, M + 348, by - 8, 3)
    note(d, W / 2, by + 108, "复制=实心主按钮，保存=描边次按钮", 19, anchor="ma", color=INK2)
    bottom_nav(d, 3)
    save(img, "wf-08-wardrobe-outfit.png")

def wf09():
    img, d = new_canvas()
    M = 60
    txt(d, M, 44, "穿搭记录", 36, True)
    cx0, cy0, cx1, cy1 = 90, 120, 630, 1080
    rr(d, [cx0, cy0, cx1, cy1], r=20, fill=(255, 255, 255), outline=(160, 160, 166), width=3)
    d.ellipse([314, 165, 406, 257], fill=(238, 239, 243))
    d.rounded_rectangle([266, 280, 454, 640], radius=60, fill=(238, 239, 243))
    d.rounded_rectangle([300, 640, 420, 980], radius=40, fill=(238, 239, 243))
    d.rounded_rectangle([286, 990, 340, 1040], radius=16, fill=(238, 239, 243))
    d.rounded_rectangle([380, 990, 434, 1040], radius=16, fill=(238, 239, 243))
    segs = [
        ("头 0.13", 140, 240, [("帽", 300, 150, 420, 230)]),
        ("上身 0.33", 240, 510, [("外套", 150, 260, 280, 480), ("上装", 295, 260, 425, 480), ("裙", 440, 260, 570, 480)]),
        ("腿 0.42", 510, 950, [("下装", 260, 530, 460, 930), ("包", 480, 560, 590, 760)]),
    ]
    for seg_label, y0, y1, items in segs:
        d.line([cx0 + 14, y0, cx1 - 14, y0], fill=(226, 227, 232), width=2)
        txt(d, cx0 + 16, y0 + 8, seg_label, 19, fill=(180, 181, 188))
        for (lb, ix0, iy0, ix1, iy1) in items:
            imgph(d, [ix0, iy0, ix1, iy1], lb, r=8)
    d.line([cx0 + 14, 950, cx1 - 14, 950], fill=(226, 227, 232), width=2)
    txt(d, cx0 + 16, 958, "脚 0.12", 19, fill=(180, 181, 188))
    dashrect(d, [300, 965, 420, 1042], label="未配鞋", label_color=INK2, sz=19)
    badge(d, cx1 - 6, 1002, 1)
    note(d, W / 2, 1108, "淡色人形轮廓 + 照片落位＝「穿在身上」；缺失品类显示空槽", 19, anchor="ma")
    x = cx0
    x = chip(d, x, 1150, "#休闲", h=44, sz=20) + 10
    chip(d, x, 1150, "#度假", h=44, sz=20)
    txt(d, cx1, 1164, "全部 5 套", 22, fill=INK2, anchor="ra")
    badge(d, 96, 1240, 2)
    d.text((122, 1262), "卡序常驻（可滑动线索）", font=font(20), fill=ACCENT, anchor="lm")
    pager_pill(d, 430, 1246, 2, 5, w=150, h=48)
    bottom_nav(d, 4)
    save(img, "wf-09-wardrobe-card.png")

def wf10():
    img, d = new_canvas()
    M = 36
    sym_chev(d, M + 24, 62, sz=17, left=True, color=INK2)
    txt(d, M + 62, 48, "穿搭 · 2026-09-19", 30, True)
    vb = [M, 120, W - M, 560]
    rr(d, vb, r=18, fill=(255, 255, 255), outline=(160, 160, 166), width=3)
    imgph(d, [vb[0] + 90, vb[1] + 30, vb[2] - 90, vb[1] + 90], "帽", r=8)
    imgph(d, [vb[0] + 30, vb[1] + 105, vb[0] + 215, vb[1] + 270], "外套", r=8)
    imgph(d, [vb[0] + 230, vb[1] + 105, vb[0] + 415, vb[1] + 270], "上装", r=8)
    imgph(d, [vb[0] + 430, vb[1] + 105, vb[2] - 30, vb[1] + 270], "配饰", r=8)
    imgph(d, [vb[0] + 150, vb[1] + 285, vb[2] - 150, vb[3] - 80], "下装", r=8)
    dashrect(d, [vb[0] + 230, vb[3] - 76, vb[2] - 30, vb[3] - 20], label="鞋 · 未配", sz=18)
    ab = [vb[2] - 210, vb[1] + 14, vb[2] - 18, vb[1] + 66]
    rr(d, ab, r=26, fill=PRIMARY)
    d.text(((ab[0] + ab[2]) / 2, (ab[1] + ab[3]) / 2), "＋ 成品图", font=font(21, True), fill=(255, 255, 255), anchor="mm")
    badge(d, vb[2] - 4, vb[1] + 40, 1)
    note(d, vb[2] - 14, vb[3] + 16, "无成品图 → 拼贴作默认主视觉；入口合并为一个角标", 19, anchor="ra")
    x = M
    x = chip(d, x, 608, "#休闲", h=44, sz=20) + 10
    chip(d, x, 608, "#早秋", h=44, sz=20)
    txt(d, M, 692, "这套包含", 27, True)
    rows = [("外套 · 飞行夹克", "军绿"), ("上装 · 白T恤", "白"), ("下装 · 工装裤", "卡其"), ("配饰 · 太阳镜", "黑")]
    for i, (nm, cl) in enumerate(rows):
        y = 746 + i * 106
        rr(d, [M, y, W - M, y + 92], r=16, fill=(255, 255, 255), outline=LINE, width=2)
        imgph(d, [M + 14, y + 12, M + 68, y + 80], "", cross=False, r=8)
        txt(d, M + 92, y + 14, nm, 24, True)
        txt(d, M + 92, y + 56, cl, 21, fill=INK2)
        sym_chev(d, W - M - 30, y + 46, sz=16, left=False, color=(170, 171, 178))
    badge(d, M + 40, 746 + 2 * 106 - 12, 2)
    note(d, W / 2, 1206, "行加箭头与按压态 → 点单品直达衣物详情", 19, anchor="ma")
    txt(d, M, 1252, "评论 (1)", 25, True)
    txt(d, M, 1300, "9/19  被同事夸了", 22, fill=INK2)
    rr(d, [M, 1340, W - M, 1408], r=14, fill=FILL, outline=LINE, width=2)
    txt(d, M + 20, 1362, "说点什么…", 22, fill=INK2)
    txt(d, W - M - 18, 1362, "发送", 22, fill=ACCENT, anchor="ra")
    save(img, "wf-10-wardrobe-record.png")

def wf11():
    img, d = new_canvas()
    M = 36
    d.rectangle([0, 0, W, 150], fill=(120, 120, 128))
    sy = 150
    d.rectangle([0, sy, W, H], fill=(255, 255, 255))
    rr(d, [W / 2 - 40, sy + 16, W / 2 + 40, sy + 26], r=5, fill=(200, 200, 205))
    txt(d, W / 2, sy + 44, "导出生图素材", 30, True, anchor="ma")
    pb = [M, sy + 96, M + 250, sy + 520]
    rr(d, pb, r=14, fill=(247, 247, 249), outline=LINE, width=2)
    for i in range(4):
        imgph(d, [pb[0] + 22, pb[1] + 20 + i * 88, pb[2] - 22, pb[1] + 88 + i * 88], f"单品{i+1}", r=6)
    txt(d, pb[2] - 22, pb[1] + 440, "+ Prompt 底部", 19, fill=INK2, anchor="ra")
    rx = pb[2] + 34
    txt(d, rx, sy + 100, "打开即见预览", 22, True)
    txt(d, rx, sy + 140, "不再先滚维度", 20, fill=INK2)
    badge(d, rx + 150, sy + 118, 1)
    txt(d, M, sy + 560, "场景", 24, fill=INK2)
    x = M + 80
    x = chip(d, x, sy + 544, "街头", selected=True, check=True) + 10
    x = chip(d, x, sy + 544, "办公室") + 10
    chip(d, x, sy + 544, "咖啡馆")
    fy = sy + 630
    rr(d, [M, fy, W - M, fy + 74], r=16, fill=ACC_BG, outline=(190, 210, 255), width=2)
    txt(d, M + 22, fy + 20, "更多：氛围 · 季节 · 光线 · 构图", 23, True, fill=(40, 80, 200))
    sym_chev(d, M + 40, fy - 18, sz=11, left=True, color=(40, 80, 200))
    badge(d, W - M + 8, fy + 37, 2)
    note(d, M, fy + 92, "折叠 + 记住上次选择", 19)
    ry = fy + 146
    txt(d, M, ry + 12, "人物", 24, fill=INK2)
    rr(d, [M + 80, ry, W - M, ry + 62], r=12, fill=FILL, outline=LINE, width=2)
    txt(d, M + 100, ry + 16, "一句描述自己（记住上次）", 21, fill=INK2)
    py = ry + 96
    txt(d, M, py - 34, "文案（实时 · 可编辑）", 22, True)
    rr(d, [M, py, W - M, py + 200], r=14, fill=(40, 40, 46))
    txt(d, M + 22, py + 20, "请根据这张长图从上到下的顺序，", 21, fill=(235, 235, 240))
    txt(d, M + 22, py + 56, "为一位身高 175cm 的男生生成", 21, fill=(235, 235, 240))
    txt(d, M + 22, py + 92, "真人街拍…（等宽字、单层容器）", 21, fill=(160, 200, 255))
    badge(d, M - 6, py - 8, 3)
    byy = py + 240
    btn(d, M, byy, 380, 86, "复制长图", sz=27)
    rr(d, [W - M - 220, byy, W - M, byy + 86], r=43, outline=(150, 150, 156), width=2)
    d.text((W - M - 110, byy + 43), "分享", font=font(25), fill=INK, anchor="mm")
    sn = [M, byy + 118, W - M, byy + 178]
    rr(d, sn, r=12, fill=(52, 52, 58))
    d.text((W / 2 - 14, (sn[1] + sn[3]) / 2), "已复制长图与文案", font=font(22), fill=(255, 255, 255), anchor="mm")
    sym_check(d, W / 2 + 120, (sn[1] + sn[3]) / 2, sz=16, color=(255, 255, 255))
    badge(d, W - M + 8, byy + 148, 4)
    txt(d, W / 2, byy + 208, "＋ 录入成品图      ☆ 收藏（次级，降一档）", 21, fill=INK2, anchor="ma")
    save(img, "wf-11-export.png")

def wf12():
    img, d = new_canvas()
    M = 36
    d.rectangle([0, 0, W, 420], fill=(120, 120, 128))
    sy = 420
    d.rectangle([0, sy, W, H], fill=(255, 255, 255))
    rr(d, [W / 2 - 40, sy + 16, W / 2 + 40, sy + 26], r=5, fill=(200, 200, 205))
    txt(d, W / 2, sy + 50, "切换衣橱", 30, True, anchor="ma")
    roles = [("Leo", True), ("老婆", False), ("宝宝", False)]
    for i, (nm, cur) in enumerate(roles):
        y = sy + 110 + i * 128
        if cur:
            rr(d, [M, y, W - M, y + 110], r=18, fill=ACC_BG, outline=(190, 210, 255), width=2)
        else:
            rr(d, [M, y, W - M, y + 110], r=18, outline=LINE, width=2)
        sym_person(d, M + 56, y + 26, r=12)
        txt(d, M + 110, y + 36, nm, 27, True)
        if cur:
            d.text((W - M - 116, y + 44), "使用中", font=font(24, True), fill=(40, 80, 200), anchor="lm")
            sym_check(d, W - M - 140, y + 55, sz=20, color=(40, 80, 200))
            badge(d, M + 16, y - 10, 1)
        else:
            txt(d, W - M - 20, y + 42, "点击切换", 21, fill=(120, 121, 128), anchor="ra")
    ny = sy + 110 + 3 * 128 + 24
    btn(d, M, ny, 320, 84, "＋ 新建角色", sz=25)
    d.text((W - M - 76, ny + 42), "管理", font=font(24), fill=INK2, anchor="lm")
    sym_chev(d, W - M - 48, ny + 42, sz=13, left=False, color=INK2)
    badge(d, W / 2 - 148, ny + 116, 2)
    note(d, W / 2 + 16, ny + 106, "新建=实心主按钮，管理=文字入口", 19, anchor="ma")
    save(img, "wf-12-roles.png")

def wf13():
    img, d = new_canvas()
    M = 36
    txt(d, M, 44, "衣橱 · Leo", 36, True)
    txt(d, W - M, 54, "共 17 件", 23, fill=INK2, anchor="ra")
    ty = 118
    tabs = [("全部", True), ("上装", False), ("外套", False), ("下装", False), ("裙", False), ("鞋", False)]
    x = M
    for lb, sel in tabs:
        wch = 96 if lb != "全部" else 88
        if sel:
            rr(d, [x, ty, x + wch, ty + 92], r=14, fill=FILL, outline=(120, 122, 132), width=3)
        else:
            rr(d, [x, ty, x + wch, ty + 92], r=14, outline=LINE, width=2)
        d.ellipse([x + wch / 2 - 22, ty + 10, x + wch / 2 + 22, ty + 54], fill=FILL2, outline=LINE, width=2)
        d.text((x + wch / 2, ty + 74), lb, font=font(19), fill=INK if sel else INK2, anchor="ma")
        x += wch + 12
    rr(d, [W - M - 110, ty, W - M, ty + 92], r=14, fill=ACC_BG, outline=(190, 210, 255), width=2)
    d.text((W - M - 55, ty + 34), "筛选", font=font(22, True), fill=(40, 80, 200), anchor="mm")
    d.ellipse([W - M - 26, ty + 16, W - M - 10, ty + 32], fill=(47, 107, 255))
    badge(d, M - 4, ty + 20, 1)
    note(d, W / 2, 232, "品类 Tab 保留单行，标签收进「筛选」（角标计数）", 19, anchor="ma", color=ACCENT)
    gy = 272
    names = [("白T恤", "白 · #通勤"), ("球衣", "藏青 · #运动"), ("牛津纺衬衫", "白蓝条 · #早秋"), ("羽绒服", "黑 · #冬")]
    for i, (nm, meta) in enumerate(names):
        col, row = i % 2, i // 2
        x0 = M + col * 328
        y0 = gy + row * 300
        rr(d, [x0, y0, x0 + 316, y0 + 284], r=16, fill=(255, 255, 255), outline=LINE, width=2)
        imgph(d, [x0 + 14, y0 + 14, x0 + 302, y0 + 200], "照片", r=10)
        txt(d, x0 + 18, y0 + 214, nm, 24, True)
        txt(d, x0 + 18, y0 + 252, meta, 20, fill=INK2)
    badge(d, M + 316 * 2 - 30, gy + 560, 2)
    note(d, W - M, gy + 606, "卡片网格：首屏 4-6 件；去掉行内品类小标", 19, anchor="ra")
    d.ellipse([W - M - 92, 1374, W - M, 1466], fill=PRIMARY)
    d.text((W - M - 46, 1420), "＋", font=font(40, True), fill=(255, 255, 255), anchor="mm")
    bottom_nav(d, 5)
    save(img, "wf-13-wardrobe-list.png")

def wf14():
    img, d = new_canvas()
    M = 36
    sym_chev(d, M + 24, 62, sz=17, left=True, color=INK2)
    txt(d, W / 2, 50, "衣物详情", 28, True, anchor="ma")
    txt(d, W - M, 56, "编辑", 23, fill=INK2, anchor="ra")
    pb = [M, 120, W - M, 620]
    rr(d, pb, r=20, fill=(243, 244, 247), outline=(178, 179, 186), width=3)
    imgph(d, [pb[0] + 40, pb[1] + 40, pb[2] - 40, pb[3] - 40], "衣物照片（统一浅底容器 · 固定比例）")
    badge(d, pb[2] + 4, pb[1] + 30, 1)
    note(d, W / 2, 640, "无论原图背景如何，容器统一底色/圆角/比例", 19, anchor="ma")
    txt(d, M, 700, "牛津纺衬衫 · 白蓝条纹", 30, True)
    txt(d, M, 758, "宽松棉质、纽扣领", 24, fill=INK2)
    x = M
    x = chip(d, x, 806, "#通勤", h=44, sz=21) + 10
    x = chip(d, x, 806, "#早秋", h=44, sz=21) + 10
    chip(d, x, 806, "#棉质", h=44, sz=21)
    txt(d, M, 896, "这件衣服穿过这些穿搭", 26, True)
    for i in range(2):
        x0 = M + i * 328
        y0 = 950
        rr(d, [x0, y0, x0 + 316, y0 + 300], r=16, fill=(255, 255, 255), outline=LINE, width=2)
        imgph(d, [x0 + 20, y0 + 20, x0 + 296, y0 + 200], f"穿搭缩略 {i+1}", r=10)
        txt(d, x0 + 20, y0 + 216, ["9/19 通勤", "9/12 周末"][i], 22, True)
        d.text((x0 + 20, y0 + 262), "点开看整套", font=font(20), fill=ACCENT, anchor="lm")
        sym_chev(d, x0 + 126, y0 + 268, sz=11, left=False, color=ACCENT)
    badge(d, M + 640, 950 + 20, 2)
    note(d, W / 2, 1290, "缩略放大为卡、可点预期明确", 19, anchor="ma")
    txt(d, M, 1330, "评论 (2)", 24, True)
    txt(d, M, 1376, "9/12  洗后微缩水 · 8/03  搭西装好看", 21, fill=INK2)
    save(img, "wf-14-item-detail.png")


if __name__ == "__main__":
    for fn in [wf01, wf02, wf03, wf04, wf05, wf06, wf07, wf15, wf08, wf09, wf10, wf11, wf12, wf13, wf14]:
        fn()
    print("all done")
