#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""R2 轮改版线框（灰盒 + 蓝徽标变更点），输出 reports/2026-09-20-ux-review-r2/assets/wf-*.png"""
import sys, os
sys.path.insert(0, os.path.expanduser("~/.agents/skills/ui-audit/scripts"))
from wireframe_lib import *
from PIL import Image, ImageDraw

OUT = os.path.join(os.path.dirname(__file__), "..", "assets")
os.makedirs(OUT, exist_ok=True)

def save(img, name):
    p = os.path.join(OUT, name)
    img.save(p)
    print("saved", name)

# ---------------- wf1 · R5 衣橱列表（P0 长按删除可发现性 + 筛选溢出 + 网格节奏） ----------------
img, d = new_canvas()
txt(d, 36, 40, "衣橱 · Leo", 38, True)
txt(d, W - 36, 52, "17 件", 22, fill=INK2, anchor="ra")
# 品类 Tab：纯图标行 + 固定筛选（常驻可见）
x = 36
for i, lb in enumerate(["全", "", "", "", "", "", "", "", ""]):
    if i == 0:
        x = chip(d, x, 110, "全部", selected=True) + 10
    else:
        rr(d, [x, 110, x + 52, 162], r=26, fill=FILL, outline=LINE, width=2)
        d.ellipse([x + 14, 122, x + 38, 146], outline=INK2, width=3)
        x += 62
badge(d, 46, 193, 1)
note(d, 76, 182, "品类改纯图标 Tab，一屏放得下", 19)
# 筛选固定在行尾（不溢出）
rr(d, [W - 36 - 92, 110, W - 36, 162], r=26, outline=ACCENT, width=3)
sym_magnifier(d, W - 36 - 66, 136, r=11)
d.text((W - 36 - 44, 136), "筛选", font=font(22), fill=INK, anchor="lm")
note(d, W - 36, 84, "筛选常驻行尾", 19, anchor="ra")
badge(d, W - 220, 90, 2)
# 网格卡片 ×4：照片+名称，右上 ··· 菜单
for i, (cx, cy) in enumerate([(36, 250), (384, 250), (36, 560), (384, 560)]):
    rr(d, [cx, cy, cx + 300, cy + 280], r=14, fill=(250, 250, 252), outline=LINE, width=2)
    imgph(d, [cx + 14, cy + 14, cx + 286, cy + 200], cross=False, r=10)
    txt(d, cx + 16, cy + 210, ["白T恤", "牛津纺衬衫", "工装裤", "飞行夹克"][i], 24, True)
    txt(d, cx + 16, cy + 246, ["白色", "浅蓝", "军绿", "橄榄"][i], 19, fill=INK2)
    # ··· 角标菜单
    d.ellipse([cx + 258, cy + 24, cx + 266, cy + 32], fill=INK2)
    d.ellipse([cx + 258, cy + 38, cx + 266, cy + 46], fill=INK2)
    d.ellipse([cx + 258, cy + 52, cx + 266, cy + 60], fill=INK2)
badge(d, 96, 264, 3)
note(d, 36, 872, "卡片右上 ··· = 编辑/删除入口（替代无提示的长按）", 19)
# 名称下颜色独立胶囊
rr(d, [36, 900, 96, 934], r=17, fill=FILL2)
txt(d, 66, 917, "白", 19, anchor="mm", fill=INK)
note(d, 116, 917, "颜色改色块胶囊，与 #标签 分离", 19, anchor="lm")
badge(d, W - 46, 917, 4)
txt(d, 36, 990, "全部 N 件 · 按品类排序，无小节标题打断", 20, fill=INK2)
bottom_nav(d, ["搭配", "穿搭记录", "衣橱"], active=2)
save(img, "wf-01-wardrobe-list.png")

# ---------------- wf2 · R3 穿搭记录（N3 双拼贴语言统一） ----------------
img, d = new_canvas()
txt(d, 36, 40, "穿搭记录 · Leo", 36, True)
btn(d, W - 36 - 190, 96, 190, 64, "随机翻一套", primary=False, sz=22)
# 卡组主卡：BodyCollage（人形）
rr(d, [96, 200, 624, 830], r=18, fill=(250, 250, 252), outline=LINE, width=2)
d.ellipse([300, 230, 420, 330], fill=FILL2)          # 头
rr(d, [230, 340, 490, 540], r=40, fill=FILL2)         # 上身
rr(d, [270, 550, 450, 760], r=36, fill=FILL2)         # 腿
rr(d, [280, 775, 440, 810], r=14, fill=FILL2)         # 脚
txt(d, 360, 420, "拼贴卡", 22, fill=INK2, anchor="mm")
pager_pill(d, W / 2, 872, 1, 5)
# 下方网格：迷你人形拼贴（与卡组同语言）
txt(d, 36, 930, "全部 5 套", 24, True)
for cx in (36, 384):
    rr(d, [cx, 980, cx + 300, 1360], r=14, outline=LINE, width=2)
    d.ellipse([cx + 118, 1000, cx + 182, 1050], fill=FILL2)
    rr(d, [cx + 88, 1058, cx + 212, 1180], r=24, fill=FILL2)
    rr(d, [cx + 108, 1188, cx + 192, 1300], r=20, fill=FILL2)
    rr(d, [cx + 118, 1310, cx + 182, 1340], r=10, fill=FILL2)
badge(d, 96, 994, 1)
note(d, 36, 1390, "网格缩略与卡组同一套人形拼贴（含空槽），一页一种语言", 19)
bottom_nav(d, ["搭配", "穿搭记录", "衣橱"], active=1)
save(img, "wf-02-records.png")

# ---------------- wf3 · E1 吃啥首页（筛选差异化 + 卡片减负） ----------------
img, d = new_canvas()
txt(d, 36, 40, "今天吃啥", 38, True)
x = 36
x = chip(d, x, 116, "堂食", selected=True, check=True) + 10
x = chip(d, x, 116, "外卖") + 10
x = chip(d, x, 116, "自做") + 10
# 筛选改 AssistChip：外框 + 前缀漏斗（画梯形）
rr(d, [x, 116, x + 132, 168], r=27, outline=ACCENT, width=3)
fx = x + 24
d.polygon([(fx, 128), (fx + 26, 128), (fx + 20, 142), (fx + 20, 156), (fx + 8, 156), (fx + 8, 142)], outline=INK, width=3)
d.text((fx + 34, 142), "筛选", font=font(23), fill=INK, anchor="lm")
badge(d, x + 150, 142, 1)
note(d, 36, 188, "筛选=描边+漏斗前缀，与类型 chip 视觉分离", 19)
# 卡组
rr(d, [60, 240, 660, 1050], r=22, fill=(250, 250, 252), outline=LINE, width=2)
rr(d, [84, 264, 636, 470], r=12, fill=FILL)   # hero
badge(d, 630, 240, 2)
pager_pill(d, W / 2, 252, 1, 9)
note(d, W - 36, 300, "胶囊上移出卡面", 19, anchor="ra")
txt(d, 84, 490, "巷子深火锅", 30, True)
chip(d, 84, 540, "堂食", sz=20)
txt(d, 84, 606, "2 天前 · 4 次 · 火锅", 20, fill=INK2)
# 链接折叠为一行
rr(d, [84, 646, 400, 690], r=22, fill=FILL, outline=LINE, width=2)
d.ellipse([104, 660, 116, 672], fill=(232, 163, 61))
txt(d, 128, 668, "美团 · 招牌套餐", 20, anchor="lm")
sym_chev(d, 382, 668, sz=12, left=False, color=INK2)
badge(d, 420, 668, 3)
note(d, 36, 716, "多链接收一行 + › 展开；卡内只留一个主操作", 19)
btn(d, 84, 930, 330, 76, "＋ 记一笔", sz=24)
btn(d, 434, 930, 202, 76, "详情", primary=False, sz=24)
bottom_nav(d, ["吃什么", "地图", "列表"], active=0)
save(img, "wf-03-eats-home.png")

# ---------------- wf4 · E2 抽取落定（重心下沉 + 动作归一） ----------------
img, d = new_canvas()
txt(d, 36, 40, "今天吃啥", 38, True)
chip(d, 36, 116, "堂食", selected=True, check=True)
# 收拢卡组（顶部一小截，变暗）
rr(d, [96, 220, 624, 320], r=16, fill=FILL2)
rr(d, [126, 200, 594, 290], r=16, fill=(236, 236, 240))
txt(d, 360, 252, "卡组收拢（变暗 30%）", 19, fill=INK2, anchor="mm")
badge(d, 96, 220, 1)
# 深色结果块：下移贴导航
rr(d, [48, 900, 672, 1360], r=24, fill=(29, 40, 34))
txt(d, 360, 950, "今天就吃", 24, fill=(180, 200, 185), anchor="mm")
txt(d, 360, 1010, "巷子深火锅", 52, True, fill=(245, 250, 243), anchor="mm")
txt(d, 360, 1090, "堂食 · 火锅 · 候选 9 家", 22, fill=(170, 190, 175), anchor="mm")
badge(d, 96, 900, 2)
note(d, W - 44, 920, "结果块贴底 12dp，重心下沉", 19, anchor="ra")
btn(d, 78, 1150, 330, 92, "就吃这个", primary=True, check=True, sz=26)
# 深底上的次按钮手画描边
rr(d, [420, 1150, 642, 1242], r=46, outline=(235, 240, 233), width=3)
txt(d, 531, 1196, "再抽", 26, True, fill=(235, 240, 233), anchor="mm")
# 彩屑点缀（自绘小方块/圆点）
import random
random.seed(7)
for (cx, cy) in [(120, 880), (600, 870), (200, 840), (520, 830), (360, 815)]:
    d.rectangle([cx, cy, cx + 12, cy + 12], fill=(255, 176, 32))
    d.ellipse([cx + 30, cy + 4, cx + 42, cy + 16], fill=(90, 170, 120))
badge(d, 640, 820, 3)
note(d, 36, 1288, "确认/再抽与底部动作合并，落定态仅此一行动作", 19)
bottom_nav(d, ["吃什么", "地图", "列表"], active=0)
save(img, "wf-04-eats-drawn.png")

# ---------------- wf5 · R8 导出面板（预览自适应 + 动作钉住） ----------------
img, d = new_canvas()
rr(d, [0, 140, W, H], r=0, fill=(250, 250, 252))
rr(d, [W / 2 - 60, 164, W / 2 + 60, 176], r=6, fill=LINE)   # grab handle
txt(d, 36, 200, "导出生图素材", 32, True)
# 预览：自适应高（到 1/2 屏）
rr(d, [36, 260, 684, 900], r=16, outline=LINE, width=2)
for yy in (300, 420, 540, 660, 780):
    rr(d, [70, yy, 650, yy + 96], r=10, fill=FILL)
badge(d, 96, 260, 1)
note(d, W - 44, 250, "预览自适应屏高一半", 19, anchor="ra")
# 动作栏钉住（不被折叠区推走）
btn(d, 36, 956, 400, 84, "复制长图", sz=26)
btn(d, 456, 956, 228, 84, "分享", primary=False, sz=24)
rr(d, [36, 1060, 684, 1118], r=29, outline=LINE, width=2)
txt(d, 60, 1088, "只复制文本（含清单）", 21, fill=INK, anchor="lm")
badge(d, 96, 1088, 2)
note(d, 36, 1136, "三个动作钉在预览下，展开维度也不下移", 19)
# 场景常驻 + 折叠
txt(d, 36, 1180, "场景", 21, fill=INK2)
x = 36
for lb in ["城市街头", "办公室", "咖啡馆"]:
    x = chip(d, x, 1210, lb) + 8
rr(d, [36, 1290, 684, 1352], r=16, fill=ACC_BG, outline=ACCENT, width=2)
txt(d, 60, 1320, "更多维度", 22, fill=INK, anchor="lm")
rr(d, [200, 1306, 250, 1336], r=15, fill=ACCENT)
txt(d, 225, 1321, "2", 20, True, fill=(255, 255, 255), anchor="mm")
sym_triangle_down(d, 660, 1321, sz=9)
badge(d, 96, 1320, 3)
note(d, 36, 1372, "折叠条选中计数徽标高亮", 19)
save(img, "wf-05-export.png")

# ---------------- wf6 · R4 穿搭详情（角标移位 + 命名统一） ----------------
img, d = new_canvas()
txt(d, 36, 36, "穿搭 · 09/20", 28, True)
sym_chev(d, 690, 50, sz=14, left=True)
# 拼贴主视觉
rr(d, [96, 110, 624, 700], r=18, fill=(250, 250, 252), outline=LINE, width=2)
d.ellipse([310, 140, 410, 230], fill=FILL2)
rr(d, [240, 240, 480, 420], r=30, fill=FILL2)
rr(d, [280, 430, 440, 610], r=26, fill=FILL2)
dashrect(d, [300, 622, 420, 668], label="未配鞋")
# 录入按钮移到拼贴右上（远离空槽）
rr(d, [438, 122, 612, 168], r=23, fill=(66, 158, 104))
sym_camera(d, 462, 145, w=26)
txt(d, 486, 145, "录入成品图", 20, True, fill=(255, 255, 255), anchor="lm")
badge(d, 96, 122, 1)
note(d, 36, 716, "角标移到拼贴右上，不再贴着「未配鞋」", 19)
# 单品行
txt(d, 36, 760, "这套包含", 26, True)
for i, lb in enumerate(["帽子 · 牛仔帽", "上装 · 白T恤", "下装 · 工装裤"]):
    rr(d, [36, 810 + i * 92, 684, 886 + i * 92], r=14, fill=(250, 250, 252), outline=LINE, width=2)
    imgph(d, [50, 824 + i * 92, 106, 872 + i * 92], cross=False, r=8)
    txt(d, 122, 840 + i * 92, lb, 22)
    sym_chev(d, 664, 848 + i * 92, sz=13, left=False, color=INK2)
btn(d, 36, 1100, 648, 88, "复制长图（与搭配页同名）", sz=25)
badge(d, 96, 1144, 2)
note(d, 36, 1210, "「复制素材」更名「复制长图」，与 W1 一套词", 19)
save(img, "wf-06-record-detail.png")

# ---------------- wf7 · E7/R7 表单保存栏（两态细节 + 照片空占位） ----------------
img, d = new_canvas()
txt(d, 36, 36, "添加衣物", 32, True)
sym_x(d, 690, 52)
# 照片空占位（大虚线预览区）
dashrect(d, [170, 110, 550, 510], label="拍照 / 选照片 · 第一步")
sym_camera(d, 360, 300, w=56)
badge(d, 186, 122, 1)
note(d, 36, 530, "必填照片给大预览占位：进页面即知道第一步", 19)
txt(d, 36, 590, "名称 *", 22, fill=INK2)
rr(d, [36, 626, 684, 692], r=12, outline=LINE, width=2)
d.text((160, 659), "白色牛津纺衬衫", font=font(23), fill=INK, anchor="lm")
txt(d, 36, 726, "品类", 22, fill=INK2)
x = 36
for lb in ["帽子", "外套", "上装", "下装"]:
    x = chip(d, x, 762, lb, selected=(lb == "上装")) + 8
# 吸底保存栏：按钮全宽两态
d.line([0, 1300, W, 1300], fill=LINE, width=2)
d.rectangle([0, 1300, W, H], fill=(255, 255, 255))
btn(d, 36, 1330, 648, 88, "保存", sz=27)
badge(d, 96, 1374, 2)
note(d, W - 36, 1374, "就绪=全宽实心", 19, anchor="ra")
txt(d, 360, 1560, "（未就绪：全宽描边 + 中性灰原因「还差一张照片」）", 19, fill=INK2, anchor="mm")
save(img, "wf-07-form.png")

print("ALL WIREFRAMES DONE")
