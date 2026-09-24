#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""draw_wireframes.py — 2026-09-24 wardrobe 走查的 7 张灰盒改版线框。
徽标编号与报告页右侧要点严格一一对应。符号全部走 wireframe_lib 自绘。"""
import sys, os
sys.path.insert(0, "/Users/leo/Documents/mini-apps/.agents/skills/ui-audit/scripts")
from wireframe_lib import *  # noqa

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "wireframes")
os.makedirs(OUT, exist_ok=True)


def statusbar(d, seam=False):
    """顶部状态栏示意。seam=True 画出色阶接缝（现状）；改版=沉浸同色。"""
    d.rectangle([0, 0, W, 64], fill=FILL)
    txt(d, 24, 18, "11:24", 22, fill=INK2)


def sym_pencil(d, cx, cy, sz=24, color=None):
    """自绘铅笔（✎ 缺字形）。"""
    c = color or INK2
    d.line([(cx - sz * 0.5, cy + sz * 0.5), (cx + sz * 0.35, cy - sz * 0.35)],
           fill=c, width=5)
    d.line([(cx + sz * 0.35, cy - sz * 0.35), (cx + sz * 0.55, cy - sz * 0.15)],
           fill=c, width=5)
    d.line([(cx - sz * 0.5, cy + sz * 0.5), (cx - sz * 0.28, cy + sz * 0.28)],
           fill=c, width=5)


def sym_dots_v(d, cx, cy, r=4, color=None):
    """自绘竖向三点 ⋮。"""
    c = color or (255, 255, 255)
    for dy in (-13, 0, 13):
        d.ellipse([cx - r, cy + dy - r, cx + r, cy + dy + r], fill=c)


def sym_dice(d, cx, cy, s=26, color=None):
    """自绘骰子 🎲。"""
    c = color or (255, 255, 255)
    d.rounded_rectangle([cx - s / 2, cy - s / 2, cx + s / 2, cy + s / 2],
                        radius=6, outline=c, width=3)
    for dx, dy in ((-7, -7), (7, 7)):
        d.ellipse([cx + dx - 3, cy + dy - 3, cx + dx + 3, cy + dy + 3], fill=c)


def sym_bars(d, cx, cy, color=None):
    """自绘柱状图图标。"""
    c = color or INK2
    for i, h in enumerate((14, 26, 20)):
        x = cx - 14 + i * 11
        d.rectangle([x, cy + 14 - h, x + 7, cy + 14], fill=c)


def appbar_back(d, title, right_icons=True, delete_red=False):
    sym_chev(d, 42, 112, sz=26, left=True)
    txt(d, 78, 88, title, 32, True)
    if right_icons:
        sym_pencil(d, W - 140, 112)
        # 垃圾桶（自绘）：盖+桶身
        x = W - 70
        col = (220, 70, 70) if delete_red else INK2
        d.rounded_rectangle([x - 16, 100, x + 16, 132], radius=4, outline=col, width=3)
        d.line([x - 22, 96, x + 22, 96], fill=col, width=3)
        d.line([x - 8, 88, x + 8, 88], fill=col, width=3)


def fade_right(d, y0, y1, x0=W - 90, x1=W):
    """chips 行右缘渐隐（白→透）。用三段渐白矩形近似。"""
    for i in range(6):
        a = i / 6
        v = int(255 - (1 - a) * 0)
        d.rectangle([x0 + i * (x1 - x0) // 6, y0, x0 + (i + 1) * (x1 - x0) // 6, y1],
                    fill=(255, 255, 255))


# ═══════════ WF1 · W1 搭配页 ═══════════
def wf1():
    img, d = new_canvas()
    statusbar(d)
    # 顶栏：角色 + 混入心愿 + 随机一套
    txt(d, 36, 92, "Leo", 34, True)
    sym_triangle_down(d, 118, 116, sz=11)
    # 混入心愿：改版为正文色描边 pill（星用 ★ 安全字形）
    rr(d, [300, 86, 500, 142], r=28, outline=INK, width=3)
    txt(d, 330, 98, "★ 混入心愿", 24, fill=INK)
    badge(d, 156, 112, 1)
    note(d, 184, 98, "文字改正文色", 19)
    rr(d, [560, 86, 684, 142], r=28, fill=PRIMARY)
    txt(d, 586, 98, "随机一套", 24, fill=(255, 255, 255))

    # 槽位：帽（居中）
    def slot(cx, y, w, h, name, counter, two_line=False):
        rr(d, [cx - w / 2, y, cx + w / 2, y + h], r=16, outline=LINE, width=2)
        imgph(d, [cx - w / 2 + 10, y + 10, cx + w / 2 - 10, y + h - 74])
        # 名称条：名称两行 + 图 n/m + ×
        bar_y0 = y + h - 64
        d.rounded_rectangle([cx - w / 2 + 6, bar_y0, cx + w / 2 - 6, y + h - 6],
                            radius=10, fill=(70, 70, 76))
        txt(d, cx - w / 2 + 16, bar_y0 + 6, name, 19, fill=(255, 255, 255))
        if two_line:
            txt(d, cx - w / 2 + 16, bar_y0 + 30, "（第二行）", 17, fill=(220, 220, 224))
        # 计数徽标（右上角，语义化）
        rr(d, [cx + w / 2 - 96, bar_y0 + 4, cx + w / 2 - 44, bar_y0 + 32], r=14,
           fill=(255, 255, 255))
        txt(d, cx + w / 2 - 88, bar_y0 + 6, "图 1/4", 17, fill=INK)
        # × 热区（44dp 虚框示意）
        hx0, hy0 = cx + w / 2 - 40, bar_y0 - 2
        d.rectangle([hx0, hy0, hx0 + 44, hy0 + 44], outline=ACCENT, width=3)
        sym_x(d, hx0 + 22, hy0 + 22, sz=11)

    slot(W / 2, 180, 210, 240, "黑色棒球帽", "1/1")
    slot(216, 452, 250, 300, "橄榄绿工装衬衫外套", "", two_line=True)
    slot(504, 452, 250, 300, "浅蓝色亚麻衬衫", "")
    badge(d, 46, 770, 2)
    note(d, 74, 756, "名称两行·计数改「图 n/m」", 19)
    badge(d, 430, 770, 3)
    note(d, 458, 756, "× 视觉放大", 19)
    slot(178, 792, 210, 250, "浅棕色帆布托特包", "")
    slot(470, 792, 250, 300, "中蓝色直筒牛仔裤", "")
    # 对齐轨道（两条竖线）
    for x in (118, 360):
        d.line([x, 446, x, 1100], fill=ACCENT, width=2)
    badge(d, 96, 1120, 4)
    note(d, 122, 1106, "两列对齐轨道·间距吸附", 19)
    slot(W / 2, 1130, 260, 170, "白色皮革运动鞋", "")

    # 底部动作
    btn(d, 36, 1350, 320, 78, "复制长图", primary=False, sz=25)
    btn(d, W - 36 - 320, 1350, 320, 78, "保存这套", primary=True, sz=25)
    bottom_nav(d, ["搭配", "穿搭记录", "衣橱"], active=0)
    img.save(os.path.join(OUT, "wf-w1.png"))


# ═══════════ WF2 · W3 衣橱页 ═══════════
def wf2():
    img, d = new_canvas()
    statusbar(d)
    txt(d, 36, 88, "衣橱 · Leo", 34, True)
    txt(d, W - 330, 96, "共 20 件", 24, fill=INK2)
    # 两个 40dp 图标按钮（虚框=热区）
    for i, gx in enumerate((W - 170, W - 90)):
        rr(d, [gx, 84, gx + 56, 140], r=28, fill=FILL, outline=LINE, width=2)
        d.rectangle([gx - 4, 80, gx + 60, 144], outline=ACCENT, width=3)
    sym_bars(d, W - 142, 104)
    txt(d, W - 78, 96, "★", 22, fill=INK2)
    note(d, 300, 56, "图标 40dp 热区·长按 tooltip", 19)
    badge(d, 640, 66, 1)

    # chips：统一胶囊 + 右缘渐隐 + 筛选独立
    x = chip(d, 36, 172, "全部", selected=True, check=True)
    x = chip(d, x + 14, 172, "上装") + 14
    x = chip(d, x, 172, "外套") + 14
    x = chip(d, x, 172, "下装") + 14
    chip(d, x, 172, "连衣裙")
    fade_right(d, 164, 232, x0=W - 210, x1=W - 60)
    badge(d, W - 120, 250, 2)
    note(d, W - 460, 254, "统一胶囊·右缘渐隐暗示可滑", 19)
    rr(d, [W - 130, 172, W - 36, 224], r=26, outline=LINE, width=2)
    txt(d, W - 116, 184, "筛选", 22, fill=INK)

    # 两列卡片
    def card(x0, y0, name2, tags):
        rr(d, [x0, y0, x0 + 320, y0 + 430], r=18, fill=(255, 255, 255), outline=LINE, width=2)
        imgph(d, [x0 + 14, y0 + 14, x0 + 306, y0 + 280])
        txt(d, x0 + 16, y0 + 300, name2[0], 26, True)
        if len(name2) > 1:
            txt(d, x0 + 16, y0 + 334, name2[1], 26, True)
        ty = y0 + 372 if len(name2) > 1 else y0 + 338
        txt(d, x0 + 16, ty, tags, 20, fill=INK2)
        # ⋮ 44dp
        cx0, cy0 = x0 + 320 - 58, y0 + 16
        d.rectangle([cx0 - 4, cy0 - 4, cx0 + 48, cy0 + 48], outline=ACCENT, width=3)
        rr(d, [cx0, cy0, cx0 + 44, cy0 + 44], r=22, fill=(90, 90, 96))
        sym_dots_v(d, cx0 + 22, cy0 + 22)

    card(36, 272, ["浅蓝色亚麻衬衫"], "浅蓝色 · #休闲 #度假")
    card(384, 272, ["海军条纹针织", "Polo"], "米白色 · #休闲 #复古")
    badge(d, 352, 566, 3)
    note(d, 36, 726, "标题 maxLines=2·一行≥9 汉字不截断", 19)
    card(36, 736, ["奶油色针织开衫"], "奶油色 · #简约 #居家")
    card(384, 736, ["黑色罗纹高领衫"], "黑色 · #通勤 #简约")
    badge(d, 46, 1214, 4)
    note(d, 40, 1244, "图片 centerCrop·素材统一 4:3 出图杜绝邻物残片", 19)

    rr(d, [W - 110, 1180, W - 40, 1250], r=35, fill=PRIMARY)
    txt(d, W - 84, 1193, "＋", 34, fill=(255, 255, 255))
    bottom_nav(d, ["搭配", "穿搭记录", "衣橱"], active=2)
    img.save(os.path.join(OUT, "wf-w3.png"))


# ═══════════ WF3 · W5/W7 详情页 ═══════════
def wf3():
    img, d = new_canvas()
    # 沉浸同色状态栏（改版）
    d.rectangle([0, 0, W, 170], fill=(255, 255, 255))
    statusbar(d)
    badge(d, 46, 64, 1)
    note(d, 74, 52, "状态栏沉浸同色·消除色阶接缝", 19)
    appbar_back(d, "详情", delete_red=True)
    badge(d, W - 40, 70, 2)
    note(d, 360, 44, "删除=警示红·或收进⋯菜单", 19)
    # 大图 full-bleed
    imgph(d, [0, 170, W, 660], cross=False, r=0)
    txt(d, 36, 690, "浅蓝色亚麻衬衫", 34, True)
    badge(d, 340, 706, 3)
    note(d, 368, 692, "大图 full-bleed 至圆角", 19)
    txt(d, 36, 736, "浅蓝色 · 轻薄亚麻、落肩宽松", 22, fill=INK2)
    # chips 44dp
    y = 790
    x = 36
    for lb in ("#休闲", "#度假", "#春", "#夏"):
        rr(d, [x, y, x + 110, y + 56], r=28, outline=LINE, width=2)
        txt(d, x + 24, y + 14, lb, 23, fill=INK)
        x += 124
    badge(d, 60, 866, 4)
    note(d, 86, 854, "chips 高 44dp", 19)
    txt(d, 36, 890, "这件衣服穿过这些穿搭", 26, True)
    imgph(d, [36, 934, 240, 1130], cross=False)
    txt(d, 36, 1146, "点开看整套", 21, fill=INK2)
    sym_chev(d, 190, 1156, sz=12, left=False, color=INK2)
    # 评论行
    txt(d, 36, 1210, "评论（1）", 26, True)
    txt(d, 36, 1262, "09/24", 20, fill=INK2)
    txt(d, 130, 1258, "周末出门舒服，牛仔夹克卷袖子更轻松。", 21, fill=INK)
    hx, hy = W - 78, 1246
    d.rectangle([hx - 4, hy - 4, hx + 48, hy + 48], outline=ACCENT, width=3)
    sym_x(d, hx + 20, hy + 20, sz=13)
    note(d, 300, 1310, "评论删除 44dp 热区·点按先确认", 19)
    badge(d, 640, 1320, 5)
    rr(d, [36, 1360, W - 110, 1424], r=16, outline=LINE, width=2)
    txt(d, 56, 1376, "说点什么…", 22, fill=INK2)
    rr(d, [W - 100, 1360, W - 36, 1424], r=16, fill=FILL2)
    sym_arrow_ne(d, W - 68, 1392, sz=13)
    img.save(os.path.join(OUT, "wf-detail.png"))


# ═══════════ WF4 · W6 导出弹层 ═══════════
def wf4():
    img, d = new_canvas()
    statusbar(d)
    d.rounded_rectangle([0, 90, W, H], radius=28, fill=(255, 255, 255))
    d.rounded_rectangle([W / 2 - 44, 108, W / 2 + 44, 120], radius=6, fill=LINE)
    txt(d, 36, 150, "导出生图素材", 32, True)
    # Prompt 置顶
    rr(d, [36, 206, W - 36, 320], r=14, fill=(246, 244, 238))
    txt(d, 52, 218, "请根据这张长图生成真人穿搭效果图：…", 20, fill=INK)
    # 拼贴：等宽两列 + 标签完整
    cells = [(36, 344, 340, 470, "外套 · 橄榄绿工装衬衫外套"),
             (380, 344, 684, 470, "上装 · 浅蓝色亚麻衬衫"),
             (36, 490, 340, 616, "包 · 浅棕色帆布托特包"),
             (380, 490, 684, 616, "下装 · 中蓝色直筒牛仔裤")]
    for x0, y0, x1, y1, lb in cells:
        imgph(d, [x0, y0, x1, y1 - 30], cross=False)
        d.rectangle([x0, y1 - 30, x1, y1], fill=(70, 70, 76))
        txt(d, x0 + 8, y1 - 26, lb + "…", 17, fill=(255, 255, 255))
    badge(d, 46, 646, 1)
    note(d, 74, 632, "等宽两列·标签 ellipsis+内边距", 19)
    # 维度折叠
    rr(d, [36, 676, W - 36, 736], r=16, outline=LINE, width=2)
    txt(d, 56, 692, "风格与场景维度（全部可选）· 3", 23, fill=INK)
    sym_triangle_down(d, W - 70, 706, sz=12)
    # 两输入框统一
    rr(d, [36, 764, W - 36, 840], r=16, outline=LINE, width=2)
    txt(d, 56, 786, "人物描述（记住上次）", 22, fill=INK2)
    rr(d, [36, 862, W - 36, 938], r=16, outline=LINE, width=2)
    txt(d, 56, 884, "自定义要求（可选，记住上次）", 22, fill=INK2)
    badge(d, 46, 962, 2)
    note(d, 74, 948, "两输入框统一占位符规格", 19)
    # 文案区：可滚至完整 + 渐隐
    rr(d, [36, 986, W - 36, 1140], r=16, outline=LINE, width=2)
    txt(d, 56, 1000, "文案（实时生成，可编辑）", 21, fill=INK2)
    txt(d, 56, 1036, "请根据这张长图…保持每件单品的颜色", 20, fill=INK)
    txt(d, 56, 1068, "与款式一致，自然真实。另外要求：filmgrain", 20, fill=INK)
    # 渐隐条
    for i in range(5):
        v = 255 - i * 6
        d.rectangle([38, 1140 + i * 8, W - 38, 1148 + i * 8], fill=(v, v, v))
    badge(d, 46, 1212, 3)
    note(d, 74, 1198, "动作栏上方 24dp 安全区·文案可滚至完整", 19)
    # 动作栏
    btn(d, 36, 1240, 300, 76, "复制长图", primary=True, sz=24)
    btn(d, 352, 1240, 160, 76, "存相册", primary=False, sz=24)
    btn(d, 528, 1240, 156, 76, "分享", primary=False, sz=24)
    btn(d, 36, 1340, W - 72, 72, "只复制文本（含单品清单）", primary=False, sz=23)
    img.save(os.path.join(OUT, "wf-w6.png"))


# ═══════════ WF5 · W8 穿搭记录 ═══════════
def wf5():
    img, d = new_canvas()
    statusbar(d)
    txt(d, 36, 88, "穿搭记录 · Leo", 34, True)
    rr(d, [W - 250, 82, W - 36, 142], r=30, fill=PRIMARY)
    sym_dice(d, W - 222, 112)
    txt(d, W - 200, 96, "随机一套", 24, fill=(255, 255, 255))
    badge(d, W - 40, 66, 1)
    note(d, W - 430, 52, "文案与 W1 统一为「随机一套」", 19)
    # chips + fade
    x = chip(d, 36, 176, "全部", selected=True, check=True)
    x = chip(d, x + 14, 176, "#休闲") + 14
    x = chip(d, x, 176, "#秋") + 14
    x = chip(d, x, 176, "#春") + 14
    chip(d, x, 176, "#夏")
    fade_right(d, 168, 236, x0=W - 200, x1=W - 20)
    # 大卡 + 日期角标
    rr(d, [36, 272, W - 36, 810], r=20, fill=(255, 255, 255), outline=LINE, width=2)
    imgph(d, [52, 288, W - 52, 700], cross=False)
    rr(d, [68, 304, 190, 350], r=14, fill=(70, 70, 76))
    txt(d, 82, 314, "09/24", 22, fill=(255, 255, 255))
    badge(d, 214, 322, 2)
    note(d, 240, 308, "日期角标常驻·可按时间定位", 19)
    x = chip(d, 68, 724, "#休闲")
    x = chip(d, x + 12, 724, "#出游") + 12
    chip(d, x, 724, "#早秋")
    txt(d, 68, 776, "周末出游的一套", 22, fill=INK2)
    # 分页胶囊（热区放大）
    cx, cy = W / 2, 870
    rr(d, [cx - 115, cy - 32, cx + 115, cy + 32], r=32, fill=FILL, outline=LINE, width=2)
    sym_chev(d, cx - 74, cy, sz=16, left=False, color=INK2)   # 左 <
    sym_chev(d, cx + 74, cy, sz=16, left=True, color=INK2)    # 右 >
    d.text((cx, cy), "1 / 5", font=font(24, True), fill=INK, anchor="mm")
    d.rectangle([cx - 115 - 6, cy - 38, cx - 34, cy + 38], outline=ACCENT, width=3)
    d.rectangle([cx + 34, cy - 38, cx + 115 + 6, cy + 38], outline=ACCENT, width=3)
    badge(d, cx, 950, 3)
    note(d, cx + 40, 936, "整半边可点 44dp·卡片滑动为主", 19)
    # 网格（缩略统一+日期行）
    txt(d, 36, 1010, "全部 5 套", 24, True)
    for i, x0 in enumerate((36, 257, 478)):
        rr(d, [x0, 1054, x0 + 206, 1330], r=16, fill=(255, 255, 255), outline=LINE, width=2)
        imgph(d, [x0 + 10, 1064, x0 + 196, 1250], cross=False)
        txt(d, x0 + 12, 1262, "09/24 · 效果图", 19, fill=INK2)
        txt(d, x0 + 12, 1292, "#休闲 #早秋", 19, fill=INK2)
    badge(d, 46, 1372, 4)
    note(d, 74, 1358, "统一效果图缩略·无图降级拼贴并标注类型", 19)
    bottom_nav(d, ["搭配", "穿搭记录", "衣橱"], active=1)
    img.save(os.path.join(OUT, "wf-w8.png"))


# ═══════════ WF6 · W9 衣橱回顾 ═══════════
def wf6():
    img, d = new_canvas()
    statusbar(d)
    appbar_back(d, "衣橱回顾 · Leo", right_icons=False)
    # 分段控件（等分）
    sx, sy, sw, sh = W - 300, 84, 264, 56
    rr(d, [sx, sy, sx + sw, sy + sh], r=sh // 2, outline=LINE, width=2)
    rr(d, [sx + 4, sy + 4, sx + sw / 2, sy + sh - 4], r=sh // 2 - 4, fill=PRIMARY)
    txt(d, sx + 40, sy + 14, "今年", 23, fill=(255, 255, 255))
    txt(d, sx + sw * 0.72, sy + 14, "累计", 23, fill=INK)
    badge(d, sx - 24, sy - 14, 1)
    note(d, 46, 170, "等分两段·选中态用填充不加勾选", 19)
    # 三大数字
    for i, (num, lb) in enumerate((("20", "单品"), ("5", "穿搭套"), ("0", "打卡次数"))):
        cx = W * (i + 0.5) / 3
        txt(d, cx, 216, num, 44, True, anchor="ma")
        txt(d, cx, 272, lb, 22, fill=INK2, anchor="ma")
    # 空态卡
    rr(d, [36, 330, W - 36, 560], r=18, fill=FILL)
    txt(d, W / 2, 370, "还没有穿搭打卡", 28, True, anchor="ma")
    btn(d, W / 2 - 130, 460, 260, 74, "去打卡", primary=True, sz=25)
    # 品类分布：色阶 + 段内标注
    txt(d, 36, 606, "品类分布", 26, True)
    segs = [("上装 5", 5), ("外套 4", 4), ("下装 6", 6), ("鞋 2", 2), ("包 2", 2), ("帽 1", 1)]
    total = sum(v for _, v in segs)
    shades = [(196, 224, 204), (168, 208, 180), (140, 192, 156), (112, 176, 132),
              (84, 160, 108), (56, 144, 84)]
    x = 36
    for (lb, v), c in zip(segs, shades):
        wseg = (W - 72) * v / total
        d.rectangle([x, 654, x + wseg - 4, 700], fill=c)
        if wseg > 78:
            txt(d, x + 8, 662, lb, 18, fill=(255, 255, 255))
        x += wseg
    note(d, 36, 714, "同色系色阶·段内直接标注一一对应", 19)
    badge(d, 400, 722, 2)
    # 闲置/连续
    rr(d, [36, 760, W - 36, 856], r=16, fill=FILL)
    txt(d, 56, 776, "闲置清单", 24, True)
    txt(d, 56, 812, "20 件从没上过身", 21, fill=INK2)
    sym_chev(d, W - 70, 808, sz=14, left=False, color=INK2)
    rr(d, [36, 876, W - 36, 956], r=16, fill=FILL)
    txt(d, 56, 898, "连续打卡", 24, True)
    txt(d, W - 120, 898, "0 天", 24, True)
    # 年度长图（禁用+解锁说明）
    rr(d, [36, 986, W - 36, 1062], r=31, fill=(236, 238, 240), outline=LINE, width=2)
    txt(d, W / 2, 1004, "生成年度衣橱长图", 25, fill=(115, 115, 122), anchor="ma")
    txt(d, W / 2, 1074, "打卡 ≥ 3 次后解锁", 19, fill=INK2, anchor="ma")
    badge(d, 46, 1130, 3)
    note(d, 74, 1116, "禁用态补解锁条件·对比度达标", 19)
    # 衣柜提醒（开关联动）
    txt(d, 36, 1150, "衣柜提醒", 26, True)
    rr(d, [W - 110, 1148, W - 40, 1186], r=19, fill=FILL2)
    d.ellipse([W - 104, 1152, W - 76, 1180], fill=(255, 255, 255))
    x = 36
    for lb in ("60 天", "90 天", "180 天"):
        x = chip(d, x, 1210, lb, h=48) + 14
    # 关闭态：chips 降透明（画虚框+灰）
    d.rectangle([30, 1204, x + 6, 1264], outline=ACCENT, width=3)
    badge(d, 46, 1298, 4)
    note(d, 74, 1284, "总开关关闭→chips 降透明·选中态统一品牌绿", 19)
    # 数据行（演示模式禁用）
    txt(d, 36, 1340, "数据", 26, True)
    d.rounded_rectangle([36, 1384, W - 36, 1444], radius=14, fill=FILL)
    txt(d, 56, 1400, "导出数据包", 23, fill=(150, 150, 156))
    rr(d, [W - 240, 1396, W - 116, 1434], r=19, fill=(226, 228, 235))
    txt(d, W - 232, 1404, "演示模式", 18, fill=INK2)
    badge(d, 660, 1414, 5)
    note(d, 36, 1460, "禁用行降透明去箭头·行内徽标说明", 19)
    img.save(os.path.join(OUT, "wf-w9.png"))


# ═══════════ WF7 · W10 心愿页 ═══════════
def wf7():
    img, d = new_canvas()
    statusbar(d)
    appbar_back(d, "心愿", right_icons=False)
    # 统一分段（连体）
    sx, sy, sw, sh = 36, 176, 470, 60
    rr(d, [sx, sy, sx + sw, sy + sh], r=sh // 2, outline=LINE, width=2)
    rr(d, [sx + 4, sy + 4, sx + sw / 2, sy + sh - 4], r=sh // 2 - 4, fill=PRIMARY)
    txt(d, sx + 40, sy + 16, "★ 想买单品 2", 23, fill=(255, 255, 255))
    txt(d, sx + sw * 0.66, sy + 16, "心愿穿搭 0", 23, fill=INK)
    badge(d, 530, 200, 1)
    note(d, 558, 186, "回顾页同规格", 19)
    # 品类 chips + fade
    x = chip(d, 36, 268, "全部", selected=True, check=True)
    x = chip(d, x + 14, 268, "上装") + 14
    x = chip(d, x, 268, "外套") + 14
    x = chip(d, x, 268, "下装") + 14
    chip(d, x, 268, "连衣裙")
    fade_right(d, 260, 328, x0=W - 210, x1=W - 20)
    badge(d, W - 110, 350, 2)
    note(d, 260, 352, "右缘渐隐·保留下一 chip 露头", 19)
    # 分组标题
    d.rectangle([36, 404, 44, 444], fill=INK)
    txt(d, 58, 404, "外套", 26, True)
    # 无图心愿卡：品类色块占位
    rr(d, [36, 464, W - 36, 660], r=20, fill=(255, 255, 255), outline=LINE, width=2)
    rr(d, [56, 484, 216, 640], r=16, fill=(226, 236, 246))
    txt(d, 136, 540, "★", 46, fill=(47, 107, 255), anchor="ma")
    txt(d, 136, 600, "外套", 22, fill=INK, anchor="ma")
    txt(d, 240, 492, "灰蓝色羊毛大衣", 30, True)
    txt(d, 240, 536, "¥1599", 24, True, fill=(46, 140, 90))
    txt(d, 348, 538, "灰蓝色", 22, fill=INK2)
    txt(d, 240, 574, "example.com", 20, fill=(180, 180, 186))
    txt(d, 240, 610, "#正式 #简约 #冬 #通勤", 21, fill=INK2)
    txt(d, W - 130, 546, "今天", 20, fill=INK2)
    note(d, 360, 436, "品类色块占位·告别空白洞", 19)
    badge(d, 620, 430, 3)
    note(d, 360, 686, "域名弱化次级行·元信息不混排", 19)
    badge(d, 660, 696, 4)
    # 第二组
    d.rectangle([36, 720, 44, 760], fill=INK)
    txt(d, 58, 720, "包", 26, True)
    rr(d, [36, 780, W - 36, 940], r=20, fill=(255, 255, 255), outline=LINE, width=2)
    rr(d, [56, 800, 216, 920], r=16, fill=(246, 234, 222))
    txt(d, 136, 832, "★", 40, fill=(47, 107, 255), anchor="ma")
    txt(d, 136, 884, "包", 22, fill=INK, anchor="ma")
    txt(d, 240, 810, "深棕色公文托特包", 30, True)
    txt(d, 240, 856, "¥1299", 24, True, fill=(46, 140, 90))
    txt(d, 348, 858, "深棕色 · example.com", 21, fill=INK2)
    txt(d, 240, 894, "#通勤 #正式 #秋", 21, fill=INK2)
    rr(d, [W - 110, 1330, W - 36, 1400], r=35, fill=PRIMARY)
    txt(d, W - 96, 1344, "＋", 34, fill=(255, 255, 255))
    txt(d, W - 250, 1352, "种草一件", 24, fill=INK)
    img.save(os.path.join(OUT, "wf-w10.png"))


def contact_sheet():
    from PIL import Image
    paths = ["wf-w1.png", "wf-w3.png", "wf-detail.png", "wf-w6.png",
             "wf-w8.png", "wf-w9.png", "wf-w10.png"]
    thumbs = []
    for p in paths:
        im = Image.open(os.path.join(OUT, p))
        im.thumbnail((340, 756))
        thumbs.append((p, im))
    cols, rows = 4, 2
    cw, ch = 350, 780
    sheet = Image.new("RGB", (cols * cw, rows * ch), (250, 250, 250))
    for i, (p, im) in enumerate(thumbs):
        x = (i % cols) * cw + (cw - im.width) // 2
        y = (i // cols) * ch + 10
        sheet.paste(im, (x, y))
    out = os.path.join(OUT, "contact-sheet.png")
    sheet.save(out)
    print("sheet:", out)


if __name__ == "__main__":
    wf1(); wf2(); wf3(); wf4(); wf5(); wf6(); wf7()
    contact_sheet()
    print("done ->", OUT)
