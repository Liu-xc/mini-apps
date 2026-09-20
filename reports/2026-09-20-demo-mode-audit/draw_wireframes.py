#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""2026-09-20 demo-mode 走查 · 7 张改版线框（灰盒+蓝色变更徽标）v2 自检修复版"""
import sys
sys.path.insert(0, "/Users/leo/.agents/skills/ui-audit/scripts")
from wireframe_lib import new_canvas, rr, txt, badge, note, chip, btn, dashrect, bottom_nav, sym_check, sym_chev, sym_star_row, sym_x, sym_crosshair, font, W, H, INK, INK2, LINE, FILL, FILL2, ACCENT, ACC_BG, DASH
from PIL import ImageDraw

OUT = "/Users/leo/Documents/mini-apps/reports/2026-09-20-demo-mode-audit/assets"


def banner(d, y, w=720):
    rr(d, [24, y, w - 24, y + 64], r=18, fill=(238, 222, 228))
    d.ellipse([44, y + 18, 68, y + 42], outline=(120, 60, 84), width=3)
    txt(d, 82, y + 18, "演示数据中 · 增删不保存，点按退出", sz=22, fill=(120, 60, 84))


def pager(d, cx, cy, cur, total):
    """自绘翻页胶囊（‹ › 用 sym_chev，避免 tofu）"""
    rr(d, [cx - 64, cy - 28, cx + 64, cy + 28], r=28, fill=FILL2)
    sym_chev(d, cx - 44, cy, sz=20, left=True)
    txt(d, cx, cy, f"{cur}/{total}", sz=24, anchor="mm")
    sym_chev(d, cx + 44, cy, sz=20, left=False)


def trash(d, cx, cy, color=(200, 70, 60)):
    """自绘垃圾桶"""
    d.rectangle([cx - 12, cy - 8, cx + 12, cy + 14], outline=color, width=3)
    d.line([cx - 16, cy - 12, cx + 16, cy - 12], fill=color, width=3)
    d.line([cx - 6, cy - 16, cx + 6, cy - 16], fill=color, width=3)
    d.line([cx - 5, cy - 2, cx - 5, cy + 8], fill=color, width=2)
    d.line([cx + 5, cy - 2, cx + 5, cy + 8], fill=color, width=2)


def dots_menu(d, cx, cy, color=INK):
    """自绘竖排三点菜单"""
    for dy in (-12, 0, 12):
        d.ellipse([cx - 4, cy + dy - 4, cx + 4, cy + dy + 4], fill=color)


# ---------- E1 eats 首页卡组 ----------
def e1():
    img, d = new_canvas()
    banner(d, 8)
    badge(d, 668, 100, 4)
    txt(d, 28, 96, "今天吃啥", sz=40, bold=True)
    cx = chip(d, 28, 168, "堂食", selected=True, check=True)
    cx = chip(d, cx + 16, 168, "外卖")
    chip(d, cx + 16, 168, "自做")
    pager(d, 520, 194, 1, 3)
    badge(d, 614, 194, 3)
    note(d, 28, 250, "③分页箭头加大热区、加深着色", sz=20)
    # 卡片（高度包内容）
    rr(d, [64, 300, 656, 1160], r=24, fill=(250, 250, 248), outline=LINE, width=2)
    rr(d, [100, 332, 620, 660], r=14, fill=FILL)
    txt(d, 360, 480, "菜品照片 hero", sz=24, fill=INK2, anchor="mm")
    txt(d, 108, 692, "招牌菜名 · 堂食", sz=30, bold=True)
    txt(d, 108, 746, "昨天 · 3 次 · 快餐", sz=24, fill=INK2)
    d.ellipse([112, 800, 126, 814], fill=(230, 140, 60))
    txt(d, 136, 792, "#一人食  #夜宵", sz=24, fill=INK2)
    txt(d, 136, 840, "美团·招牌套餐", sz=24)
    badge(d, 612, 700, 2)
    btn(d, 100, 1030, 250, 84, "＋ 记一笔", primary=False)
    btn(d, 370, 1030, 250, 84, "详情", primary=False)
    badge(d, 648, 1180, 1)
    note(d, 84, 1180, "①卡片高度包内容，按钮锚底消死区", sz=20)
    note(d, 84, 1216, "②选中 chip 加深 + 前置勾；卡内左缘统一栅格", sz=20)
    bottom_nav(d, ["吃什么", "地图", "列表"], active=0)
    img.save(f"{OUT}/wf-e1-card.png")


# ---------- E2 eats 落定态 ----------
def e2():
    img, d = new_canvas()
    d.rectangle([0, 0, 720, 1450], fill=(34, 48, 40))
    txt(d, 28, 30, "演示数据中 · 增删不保存，点按退出", sz=20, fill=(120, 150, 135))
    txt(d, 28, 96, "今天吃啥", sz=40, bold=True, fill=(150, 170, 158))
    chip(d, 28, 168, "堂食")
    chip(d, 130, 168, "外卖")
    rr(d, [64, 420, 656, 1160], r=26, fill=(38, 84, 62), outline=(120, 200, 150), width=4)
    txt(d, 360, 500, "就吃 · 巷子深火锅", sz=42, bold=True, fill=(255, 255, 255), anchor="mm")
    txt(d, 360, 580, "堂食 · 火锅 · 均分 4.3", sz=24, fill=(200, 230, 214), anchor="mm")
    btn(d, 100, 960, 260, 90, "就吃这个", primary=True, check=True)
    # 白底次按钮（深色卡上）
    rr(d, [380, 960, 620, 1050], r=45, fill=(255, 255, 255), outline=(200, 230, 214), width=2)
    txt(d, 500, 1005, "再抽", sz=26, fill=(38, 84, 62), anchor="mm")
    badge(d, 596, 500, 1)
    note(d, 84, 1200, "落定态隐藏「随机抽一张/换一张」", sz=20, color=(190, 220, 205))
    badge(d, 60, 1248, 2)
    note(d, 84, 1240, "全屏 scrim，结果卡成为唯一焦点", sz=20, color=(190, 220, 205))
    note(d, 84, 1290, "（彩屑只保留抽取动画，静态不留残点）", sz=20, color=(150, 170, 158))
    bottom_nav(d, ["吃什么", "地图", "列表"], active=0)
    img.save(f"{OUT}/wf-e2-drawn.png")


# ---------- E3 eats 表单/详情顶部 ----------
def e3():
    img, d = new_canvas()
    banner(d, 8)
    d.ellipse([28, 92, 68, 132], outline=INK, width=3)
    sym_chev(d, 44, 112, left=True)
    txt(d, 84, 98, "巷子深火锅", sz=30, bold=True)
    trash(d, 680, 114)
    badge(d, 622, 148, 3)
    note(d, 28, 160, "①工具栏上移：去掉 170px 空带；删除钮转危险色", sz=20)
    txt(d, 28, 224, "名称", sz=22, fill=INK2)
    rr(d, [28, 256, 692, 324], r=12, fill=(250, 250, 248), outline=LINE)
    txt(d, 28, 352, "位置（地图长按选点）", sz=22, fill=INK2)
    rr(d, [28, 384, 692, 478], r=12, fill=(250, 250, 248), outline=LINE)
    sym_crosshair(d, 66, 430, r=13)
    txt(d, 92, 418, "某某路 12 号", sz=24)
    txt(d, 600, 418, "清除", sz=22, fill=INK2)
    badge(d, 652, 352, 2)
    txt(d, 28, 506, "详细地址（可选，手填补充）", sz=22, fill=INK2)
    rr(d, [28, 538, 692, 606], r=12, fill=(250, 250, 248), outline=LINE)
    txt(d, 52, 560, "某号楼某单元", sz=24, fill=INK2)
    note(d, 28, 632, "②选点卡与详细地址拆分，各自带标签", sz=20)
    txt(d, 28, 690, "类型", sz=22, fill=INK2)
    cx = chip(d, 28, 722, "堂食", selected=True, check=True)
    cx = chip(d, cx + 16, 722, "外卖")
    chip(d, cx + 16, 722, "自做")
    txt(d, 28, 806, "评分", sz=22, fill=INK2)
    sym_star_row(d, 28, 850, filled=4)
    txt(d, 28, 910, "标签（口味 / 菜系 / 场景）", sz=22, fill=INK2)
    rr(d, [28, 942, 692, 1010], r=12, fill=(250, 250, 248), outline=LINE)
    badge(d, 660, 1210, 4)
    note(d, 28, 1210, "④滚动区底部留白 + CTA 上缘渐隐", sz=20)
    txt(d, 28, 1250, "（详情/记一笔弹层同修）", sz=20, fill=INK2)
    d.rectangle([0, 1396, 720, 1450], fill=(255, 255, 255))
    btn(d, 28, 1398, 664, 78, "保存", primary=True)
    bottom_nav(d, ["吃什么", "地图", "列表"], active=2)
    img.save(f"{OUT}/wf-e3-form.png")


# ---------- W1 wardrobe 衣橱卡 ----------
def w1():
    img, d = new_canvas()
    banner(d, 8)
    txt(d, 28, 96, "衣橱 · 我", sz=40, bold=True)
    txt(d, 396, 118, "共 12 件", sz=22, fill=INK2)
    rr(d, [560, 100, 692, 148], r=24, fill=ACC_BG)
    txt(d, 626, 116, "演示", sz=22, fill=ACCENT, anchor="mm")
    badge(d, 528, 124, 4)
    note(d, 28, 162, "④演示入口改语义文字 chip（原为图标圆钮）", sz=20)
    for i, lab in enumerate(["全部", "上装", "外套", "下装", "鞋"]):
        rr(d, [28 + i * 104, 220, 112 + i * 104, 272], r=26, fill=(FILL2 if i == 0 else FILL))
        txt(d, 70 + i * 104, 232, lab, sz=22, anchor="mm")

    def card(x, y, name, color, tags, dot):
        rr(d, [x, y, x + 320, y + 470], r=20, fill=(250, 250, 248), outline=LINE)
        rr(d, [x + 16, y + 16, x + 304, y + 320], r=12, fill=FILL)
        txt(d, x + 160, y + 160, "3D 图", sz=22, fill=INK2, anchor="mm")
        txt(d, x + 20, y + 340, name, sz=26, bold=True)
        d.ellipse([x + 24, y + 396, x + 46, y + 418], fill=dot)
        txt(d, x + 56, y + 390, color, sz=22, fill=INK)
        txt(d, x + 20, y + 428, tags, sz=22, fill=INK2)
    card(28, 300, "棉质T恤", "深绿", "#通勤 #简约", (30, 90, 60))
    card(372, 300, "星球衣", "橙红", "#运动", (215, 100, 60))
    badge(d, 298, 640, 2)
    note(d, 28, 790, "②颜色前置色点，与标签分层", sz=20)
    note(d, 300, 790, "③筛选与滚动区间距加大", sz=20)
    badge(d, 668, 268, 3)
    txt(d, 28, 850, "（每卡菜单图标去灰色圆底降权重）", sz=20, fill=INK2)
    txt(d, 28, 906, "品类·名称与图一致（种子已修 15 处）", sz=24, bold=True)
    badge(d, 668, 918, 1)
    bottom_nav(d, ["搭配", "穿搭记录", "衣橱"], active=2)
    img.save(f"{OUT}/wf-w1-wardrobe.png")


# ---------- W2 wardrobe 编辑表单 ----------
def w2():
    img, d = new_canvas()
    banner(d, 8)
    d.ellipse([28, 92, 68, 132], outline=INK, width=3)
    sym_x(d, 48, 112, sz=14)
    txt(d, 84, 98, "编辑衣物", sz=30, bold=True)
    rr(d, [28, 160, 692, 620], r=18, fill=FILL)
    txt(d, 360, 380, "照片区 35–40% 屏高", sz=24, fill=INK2, anchor="mm")
    badge(d, 640, 250, 1)
    note(d, 28, 640, "①大图卡片化并压至 40% 屏高", sz=20)
    txt(d, 28, 696, "名称", sz=22, fill=INK2)
    rr(d, [28, 728, 692, 796], r=12, fill=(250, 250, 248), outline=LINE)
    txt(d, 52, 750, "棉质T恤", sz=24)
    txt(d, 28, 824, "颜色", sz=22, fill=INK2)
    rr(d, [28, 856, 340, 924], r=12, fill=(250, 250, 248), outline=LINE)
    txt(d, 28, 952, "标签", sz=22, fill=INK2)
    rr(d, [28, 984, 692, 1052], r=12, fill=(250, 250, 248), outline=LINE)
    txt(d, 28, 1080, "描述", sz=22, fill=INK2)
    rr(d, [28, 1112, 692, 1200], r=12, fill=(250, 250, 248), outline=LINE)
    badge(d, 652, 1244, 2)
    note(d, 28, 1244, "②滚动区留白 + 按钮栏上缘渐隐", sz=20)
    d.rectangle([0, 1396, 720, 1450], fill=(255, 255, 255))
    btn(d, 28, 1398, 664, 78, "更新", primary=True)
    txt(d, 28, 1310, "（有图时文案改「更换照片」；删除入口收进菜单）", sz=20, fill=INK2)
    img.save(f"{OUT}/wf-w2-edit.png")


# ---------- W3 wardrobe 记录卡叠压 ----------
def w3():
    img, d = new_canvas()
    banner(d, 8)
    txt(d, 28, 96, "穿搭记录", sz=40, bold=True)
    txt(d, 28, 176, "标签行", sz=22, fill=INK2)
    d.rectangle([600, 168, 720, 216], fill=(255, 255, 255))
    note(d, 448, 182, "右缘渐隐", sz=20)
    badge(d, 416, 192, 3)
    rr(d, [28, 240, 692, 900], r=22, fill=(250, 250, 248), outline=LINE)
    rr(d, [52, 272, 400, 336], r=12, outline=DASH, width=2)
    txt(d, 76, 292, "未配帽（单行空条）", sz=22, fill=INK2)
    badge(d, 432, 304, 1)
    pager(d, 600, 304, 1, 3)
    badge(d, 504, 304, 2)
    txt(d, 52, 380, "外套 · 飞行夹克", sz=26, bold=True)
    rr(d, [52, 420, 380, 700], r=12, fill=FILL)
    txt(d, 52, 730, "连衣裙 · 背带裙", sz=26, bold=True)
    note(d, 92, 816, "已填槽位带品类·名称标签条", sz=20)
    badge(d, 64, 824, 4)
    txt(d, 28, 940, "①空槽压成单行条，文字不再被压", sz=20)
    txt(d, 28, 976, "②翻页胶囊移出卡片右上角", sz=20)
    note(d, 28, 1040, "详情页「录入成品图」按钮收进槽位内，", sz=20)
    note(d, 28, 1076, "文案明确「为帽子录入」", sz=20)
    bottom_nav(d, ["搭配", "穿搭记录", "衣橱"], active=1)
    img.save(f"{OUT}/wf-w3-records.png")


# ---------- W4 wardrobe 导出弹层 ----------
def w4():
    img, d = new_canvas()
    txt(d, 28, 40, "导出生图素材", sz=40, bold=True)
    rr(d, [28, 120, 388, 1180], r=16, fill=(250, 250, 248), outline=LINE)
    txt(d, 208, 300, "长图预览", sz=26, fill=INK2, anchor="mm")
    txt(d, 208, 350, "占宽 50%，标签可读", sz=21, fill=INK2, anchor="mm")
    badge(d, 60, 150, 1)
    note(d, 28, 1200, "①预览放大至 50% 宽，左侧独立", sz=20)
    txt(d, 452, 140, "风格与场景维度", sz=24, bold=True)
    txt(d, 452, 174, "全部可选", sz=20, fill=INK2)
    badge(d, 428, 150, 3)
    for i, lab in enumerate(["通勤", "休闲", "约会"]):
        chip(d, 420 + (i % 2) * 140, 200 + (i // 2) * 70, lab)
    txt(d, 420, 360, "自定义要求（可选）", sz=22, fill=INK2)
    rr(d, [420, 392, 692, 500], r=12, fill=(250, 250, 248), outline=LINE)
    txt(d, 420, 528, "人物描述", sz=22, fill=INK2)
    rr(d, [420, 560, 692, 668], r=12, fill=(250, 250, 248), outline=LINE)
    note(d, 420, 692, "②两输入框统一样式", sz=20)
    txt(d, 420, 750, "文案", sz=22, fill=INK2)
    rr(d, [420, 782, 692, 1000], r=12, fill=(250, 250, 248), outline=LINE)
    badge(d, 428, 1040, 4)
    note(d, 456, 1032, "字段底部渐隐", sz=20)
    btn(d, 28, 1398, 210, 80, "复制长图", primary=True)
    btn(d, 254, 1398, 210, 80, "存相册", primary=False)
    btn(d, 480, 1398, 212, 80, "分享", primary=False)
    img.save(f"{OUT}/wf-w4-export.png")


e1(); e2(); e3(); w1(); w2(); w3(); w4()
print("v2 done")


# ---------- E3b eats 地图 ----------
def e3map():
    img, d = new_canvas()
    rr(d, [0, 0, 720, 1450], r=0, fill=(226, 234, 226))
    for i in range(5):
        d.line([0, 200 + i * 260, 720, 140 + i * 260], fill=(210, 220, 210), width=3)
        d.line([120 + i * 120, 0, 80 + i * 120, 1450], fill=(210, 220, 210), width=3)
    # 状态栏 scrim
    for i in range(8):
        d.rectangle([0, i * 5, 720, i * 5 + 5], fill=(255, 255, 255, 12))
    d.rectangle([0, 0, 720, 46], fill=(255, 255, 255, 24))
    badge(d, 660, 26, 2)
    note(d, 28, 14, "②状态栏浅色渐变 scrim", sz=20)
    # 水滴 pin（改版：水滴形+白环）
    def pin(cx, cy, color):
        d.ellipse([cx - 26, cy - 26, cx + 26, cy + 26], outline=(255, 255, 255), width=5)
        d.polygon([(cx - 13, cy + 18), (cx + 13, cy + 18), (cx, cy + 44)], fill=color)
        d.ellipse([cx - 13, cy - 13, cx + 13, cy + 13], fill=color)
    pin(200, 480, (200, 70, 60))
    pin(420, 700, (220, 150, 40))
    pin(520, 420, (90, 160, 90))
    badge(d, 560, 460, 1)
    note(d, 28, 540, "①marker 改水滴 pin + 白环，与底图图标拉开形制", sz=20)
    # 入口胶囊 chevron
    rr(d, [28, 130, 330, 190], r=30, fill=(255, 255, 255))
    txt(d, 52, 148, "3 条未上地图（自做等）", sz=22)
    sym_chev(d, 302, 160, sz=20, left=False)
    badge(d, 356, 160, 4)
    # 定位钮右下
    d.ellipse([620, 1240, 692, 1312], fill=(255, 255, 255), outline=LINE, width=2)
    sym_crosshair(d, 656, 1276, r=18)
    badge(d, 592, 1276, 3)
    note(d, 28, 1330, "③定位钮移右下（地图惯例）", sz=20)
    bottom_nav(d, ["吃什么", "地图", "列表"], active=1)
    img.save(f"{OUT}/wf-e3-map.png")


e3map()
print("map wf ok")
