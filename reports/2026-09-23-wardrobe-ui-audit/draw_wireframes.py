#!/usr/bin/env python3
# 2026-09-23 wardrobe UI 审查 · 灰盒改版线框（W1 / W8 / W10）v2
import sys, math
sys.path.insert(0, "/Users/leo/Documents/mini-apps/.agents/skills/ui-audit/scripts")
from wireframe_lib import *  # noqa
from PIL import Image, ImageDraw

OUT = "/Users/leo/Documents/mini-apps/reports/2026-09-23-wardrobe-ui-audit/assets"

def ic_copy(d, cx, cy, sz=26, color=INK):
    p = sz // 2
    d.rectangle([cx-p+4, cy-p, cx+p, cy+p-4], outline=color, width=3)
    d.rectangle([cx-p, cy-p+4, cx+p-4, cy+p], outline=color, width=3)

def ic_star(d, cx, cy, r=14, color=INK, filled=False):
    pts = []
    for i in range(10):
        ang = -math.pi/2 + i*math.pi/5
        rad = r if i % 2 == 0 else r*0.45
        pts.append((cx+rad*math.cos(ang), cy+rad*math.sin(ang)))
    if filled:
        d.polygon(pts, fill=color)
    else:
        d.polygon(pts, outline=color, width=3)

def ic_dice(d, cx, cy, sz=30, color=INK):
    p = sz//2
    d.rounded_rectangle([cx-p, cy-p, cx+p, cy+p], 6, outline=color, width=3)
    for (fx, fy) in [(-0.35, -0.35), (0.35, 0.35), (0, 0)]:
        x, y = cx+fx*sz, cy+fy*sz
        d.ellipse([x-3, y-3, x+3, y+3], fill=color)

def noted(d, y, n, s):
    """注释行：徽标行内左侧 + 蓝字，绝不压元素。"""
    badge(d, 46, y+12, n)
    note(d, 74, y, s, 20)

# =====================================================================
# WF1 · W1 搭配页
# =====================================================================
img, d = new_canvas()
sym_person(d, 46, 64, r=20)
txt(d, 80, 48, "Leo", 30, bold=True)
sym_chev(d, 148, 62, sz=16)
ic_star(d, 420, 62, r=13, color=ACCENT, filled=True)
txt(d, 440, 47, "混入心愿", 25, fill=ACCENT)
ic_dice(d, 570, 62, sz=26)
txt(d, 592, 47, "随机一套", 25)
d.line([24, 100, 696, 100], fill=LINE, width=2)
# 顶部注释带（3 行，位于标题下、槽位上）
noted(d, 112, 1, "① emoji 功能图标 → 自绘线性图标（保留文字标签）")
noted(d, 148, 2, "② 格间距≥12dp；名称条=名称优先，品类并入计数角标")
noted(d, 184, 3, "③ 愿望卡：星标+虚线+单一名称层，不与名称条叠压")

def slot(d, x, y, w, h, cat, name, cnt, wish=False):
    rr(d, [x, y, x+w, y+h], 12, fill=FILL2 if wish else FILL,
       outline=ACCENT if wish else LINE, width=3 if wish else 2)
    if wish:
        for i in range(4):
            xx = x+18+i*((w-36)//3)
            d.line([xx, y+16, xx+10, y+16], fill=ACCENT, width=2)
            d.line([xx, y+h-16, xx+10, y+h-16], fill=ACCENT, width=2)
        ic_star(d, x+w//2, y+h//2-24, r=18, color=ACCENT)
        txt(d, x+w//2, y+h//2+8, cat+" · 想买", 22, fill=ACCENT, anchor="ma")
    else:
        bar_h = 42
        d.rounded_rectangle([x, y+h-bar_h, x+w, y+h], 10, fill="#3a3a3a")
        txt(d, x+12, y+h-bar_h+8, name, 24, bold=True, fill="#FFFFFF")
        txt(d, x+w-12, y+h-bar_h+10, cnt, 21, fill="#DDDDDD", anchor="ra")

y0 = 226
slot(d, 200, y0, 320, 150, "帽子", "渔夫帽", "1/2")
y1 = y0+162
slot(d, 24, y1, 212, 200, "外套", "飞行夹克", "2/2")
slot(d, 254, y1, 212, 200, "上装", "牛津纺衬衫", "2/3")
slot(d, 484, y1, 212, 200, "连衣裙", "背带裙", "2/2")
y2 = y1+214
slot(d, 24, y2, 320, 170, "下装", "直筒牛仔裤", "1/2")
slot(d, 362, y2+20, 156, 130, "包", "手提包", "1/2")
slot(d, 540, y2+20, 156, 130, "配饰", "太阳镜", "1/2")
y3 = y2+186
slot(d, 24, y3, 320, 130, "鞋", "小白鞋", "3/3")
slot(d, 362, y3, 334, 130, "鞋", "", "", wish=True)
btn(d, 40, 1430, 300, 78, "复制长图", primary=True)
btn(d, 380, 1430, 300, 78, "保存这套", primary=False)
ic_copy(d, 70, 1469, sz=30, color="#FFFFFF")
d.line([0, 1530, 720, 1530], fill=LINE, width=2)
bottom_nav(d, ["搭配", "穿搭记录", "衣橱"], active=0)
img.save(f"{OUT}/wireframe-01-outfit.png")

# =====================================================================
# WF2 · W8 穿搭记录
# =====================================================================
img, d = new_canvas()
txt(d, 24, 40, "穿搭记录 · Leo", 34, bold=True)
chip(d, 480, 40, "随机翻一套", w=180)
for i, t in enumerate(["全部", "#通勤", "#休闲", "#度假"]):
    chip(d, 24+i*120, 108, t, selected=(i == 0))
rr(d, [24, 190, 696, 860], 20, fill=FILL)
txt(d, 360, 500, "人体比例拼贴主视觉", 26, fill=INK2, anchor="mm")
txt(d, 360, 548, "（帽行完整可见，无浮层遮挡）", 22, fill=INK2, anchor="mm")
rr(d, [540, 210, 676, 260], 12, fill="#FFFFFF", outline=LINE, width=2)
sym_camera(d, 568, 235, w=30)
txt(d, 588, 222, "录入成品图", 21)
pager_pill(d, 360, 896, 1, 5)
txt(d, 96, 880, "横滑亦可翻张", 21, fill=INK2)
txt(d, 24, 946, "白 T 恤 · 工装裤 · 双肩包", 26, bold=True)
chip(d, 24, 996, "#休闲", w=110)
chip(d, 150, 996, "#度假", w=110)
txt(d, 696, 950, "09/13", 23, fill=INK2, anchor="ra")
for k in range(2):
    x = 24+k*348
    rr(d, [x, 1076, x+324, 1336], 16, fill=FILL)
    for r_ in range(3):
        yy = 1100+r_*78
        if (k+r_) % 2 == 0:
            dashrect(d, [x+18, yy, x+306, yy+62], label="未配鞋", label_color=INK, sz=22)
        else:
            d.rectangle([x+18, yy, x+306, yy+62], fill=FILL2)
    txt(d, x+16, 1344, "09/08", 22, fill=INK2)
noted(d, 1420, 1, "① 卡序翻页器移出拼贴区，放卡下方居中；删除两侧重复圆钮")
noted(d, 1456, 2, "② 空槽虚线加深、文字用 ink 级对比（≥3:1）")
d.line([0, 1530, 720, 1530], fill=LINE, width=2)
bottom_nav(d, ["搭配", "穿搭记录", "衣橱"], active=1)
img.save(f"{OUT}/wireframe-02-records.png")

# =====================================================================
# WF3 · W10 心愿页
# =====================================================================
img, d = new_canvas()
txt(d, 24, 40, "心愿", 34, bold=True)
chip(d, 110, 96, "想买单品 1", w=190, selected=True)
chip(d, 320, 96, "心愿穿搭 0", w=190)
txt(d, 24, 180, "▍鞋", 26, bold=True)
rr(d, [24, 230, 696, 470], 16, fill="#FFFFFF", outline=LINE, width=2)
dashrect(d, [44, 250, 200, 450], label="鞋", sz=24)
txt(d, 224, 262, "Dr Martens 1460", 30, bold=True)
txt(d, 224, 316, "¥ 899", 28, bold=True, fill=ACCENT)
txt(d, 224, 368, "黑色 · 某商城 · 种草 0 天", 22, fill=INK2)
btn(d, 520, 396, 156, 56, "已买到", primary=True, sz=23)
noted(d, 486, 1, "① 名称=衬线 Title；新增价格展示位（accent 色）")
rr(d, [0, 560, 720, 1600], 0, fill="#FFFFFF")
d.line([0, 560, 720, 560], fill=LINE, width=3)
d.line([320, 576, 400, 576], fill=LINE, width=4)
txt(d, 24, 596, "种草一件", 32, bold=True)
dashrect(d, [24, 660, 170, 806], label="商品图(可选)", sz=20)
d.rounded_rectangle([190, 660, 696, 716], 10, outline=LINE, width=2)
txt(d, 206, 676, "名称 *", 23, fill=INK2)
d.rounded_rectangle([190, 730, 430, 786], 10, outline=LINE, width=2)
txt(d, 206, 746, "价格 ¥", 23, fill=INK2)
d.rounded_rectangle([450, 730, 696, 786], 10, outline=LINE, width=2)
txt(d, 466, 746, "颜色", 23, fill=INK2)
txt(d, 24, 820, "品类 *", 23, fill=INK2)
for i, t in enumerate(["上装", "外套", "下装", "鞋", "包"]):
    chip(d, 24+i*110, 856, t, w=96, selected=(i == 3))
d.rounded_rectangle([24, 936, 696, 992], 10, outline=LINE, width=2)
txt(d, 40, 952, "商品链接（可选）", 23, fill=INK2)
d.rounded_rectangle([24, 1008, 696, 1104], 10, outline=LINE, width=2)
txt(d, 40, 1024, "描述（可选，拼进生图文案）", 23, fill=INK2)
txt(d, 24, 1124, "标签", 23, fill=INK2)
for i, t in enumerate(["通勤", "复古", "＋"]):
    chip(d, 24+i*110, 1158, t, w=96)
noted(d, 1224, 3, "③ 字段两列收紧，表单为滚动区")
d.line([0, 1400, 720, 1400], fill=LINE, width=3)
btn(d, 24, 1430, 672, 84, "收进想买", primary=True)
noted(d, 1372, 2, "② 固定动作栏常驻可视，不随表单滚走")
d.line([0, 1530, 720, 1530], fill=LINE, width=2)
img.save(f"{OUT}/wireframe-03-wishlist.png")

files = [f"{OUT}/wireframe-0{i}-{n}.png" for i, n in [(1, "outfit"), (2, "records"), (3, "wishlist")]]
th_w, th_h = 340, 756
sheet = Image.new("RGB", (th_w*3+40, th_h+20), "#FFFFFF")
for i, f in enumerate(files):
    im = Image.open(f).resize((th_w, th_h))
    sheet.paste(im, (10+i*(th_w+10), 10))
sheet.save(f"{OUT}/wireframe-contact-sheet.png")
print("v2 done")
