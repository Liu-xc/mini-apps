#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""draw.py — it-007(eats) / it-018(wardrobe) 统计回顾线框图。
复用 ui-audit 的 wireframe_lib（PIL 灰盒），输出 5 张 PNG 到本目录。"""
import os, sys

sys.path.insert(0, "/Users/leo/Documents/mini-apps/.agents/skills/ui-audit/scripts")
from wireframe_lib import (  # noqa: E402
    W, INK, INK2, LINE, FILL, FILL2, PRIMARY, ACCENT, ACC_BG,
    new_canvas, rr, txt, badge, note, chip, btn, imgph, sym_check, sym_chev, sym_person,
)

OUT = os.path.dirname(os.path.abspath(__file__))


def toggle(d, x, y, opts, sel, w=204, h=52):
    """分段切换 [今年|累计]。返回左段中心 x（供徽标定位）。"""
    seg = w // len(opts)
    rr(d, [x, y, x + w, y + h], r=h // 2, fill=FILL, outline=LINE, width=2)
    for i, o in enumerate(opts):
        sx = x + i * seg
        if i == sel:
            rr(d, [sx + 3, y + 3, sx + seg - 3, y + h - 3], r=(h - 6) // 2,
               fill=(255, 255, 255), outline=LINE, width=2)
        d.text((sx + seg / 2, y + h / 2), o, font=None or __import__("wireframe_lib").font(22, i == sel),
               fill=INK if i == sel else INK2, anchor="mm")
    return x


def hero3(d, y0, y1, items):
    rr(d, [36, y0, 684, y1], r=16, fill=FILL)
    mid = (y0 + y1) / 2
    xs = [160, 360, 560]
    for i, (val, lab) in enumerate(items):
        sz = 42 if len(val) <= 4 else 34
        d.text((xs[i], mid - 20), val, font=__import__("wireframe_lib").font(sz, True), fill=INK, anchor="mm")
        d.text((xs[i], mid + 28), lab, font=__import__("wireframe_lib").font(20), fill=INK2, anchor="mm")
    for xv in (260, 460):
        d.line([xv, y0 + 24, xv, y1 - 24], fill=LINE, width=2)


def sec_title(d, y, s):
    txt(d, 36, y, s, 28, True)


def switch(d, x0, y0, x1, y1, on=False):
    rr(d, [x0, y0, x1, y1], r=(y1 - y0) // 2, fill=(255, 255, 255), outline=LINE, width=2)
    r = (y1 - y0) // 2 - 4
    cx = (x0 + x1) / 2 + (r + 2) if on else (x0 + x1) / 2 - (r + 2)
    cy = (y0 + y1) / 2
    d.ellipse([cx - r, cy - r, cx + r, cy + r], fill=FILL2, outline=LINE, width=2)


def bars12(d, x0, base, maxh, bw, gap, vals, peak):
    for i, v in enumerate(vals):
        h = v / max(vals) * maxh
        x = x0 + i * (bw + gap)
        rr(d, [x, base - h, x + bw, base], r=4,
           fill=PRIMARY if i == peak else FILL2,
           outline=None if i == peak else LINE, width=2)


def notes(d, y, lines, dy=32):
    for i, s in enumerate(lines):
        note(d, 36, y + i * dy, s, 19)


def card_top3(d, x0, rank, name, sub, badge_n=None):
    x1 = x0 + 284
    rr(d, [x0, 320, x1, 520], r=14, fill=(255, 255, 255), outline=LINE, width=2)
    imgph(d, [x0 + 16, 336, x1 - 16, 436])
    d.ellipse([x0 + 26, 330, x0 + 62, 366], fill=FILL2, outline=LINE, width=2)
    d.text((x0 + 44, 348), str(rank), font=__import__("wireframe_lib").font(20, True), fill=INK, anchor="mm")
    txt(d, x0 + 20, 452, name, 25, True)
    txt(d, x0 + 20, 488, sub, 20, False, INK2)


# ═══════════════════ 1. eats W7 统计回顾页 ═══════════════════
def eats_w7():
    img, d = new_canvas(W, 1760)
    sym_chev(d, 56, 76, 20, left=True)
    txt(d, 96, 76, "统计回顾", 38, True, anchor="lm")
    toggle(d, 468, 50, ["今年", "累计"], 0)
    badge(d, 436, 76, 1)

    hero3(d, 130, 250, [("286", "今年顿数"), ("¥12,480", "今年花费"), ("41", "去过店数")])

    sec_title(d, 276, "最爱 TOP3")
    card_top3(d, 36, 1, "巷子深火锅", "23 次 · 均分 4.8")
    card_top3(d, 356, 2, "老王猪脚饭", "12 次 · 均分 4.6")
    sym_chev(d, 692, 420, 18, left=False)
    badge(d, 656, 306, 2)

    sec_title(d, 556, "类型占比")
    segs = [(36, 399, FILL2), (399, 593, (236, 236, 240)), (593, 684, (255, 255, 255))]
    for x0, x1, f in segs:
        rr(d, [x0, 600, x1, 648], r=0, fill=f, outline=LINE, width=2)
    for cx, s in [(217, "堂食 56%"), (496, "外卖 30%"), (638, "自做 14%")]:
        d.text((cx, 670), s, font=__import__("wireframe_lib").font(20), fill=INK2, anchor="mm")

    sec_title(d, 716, "月度节奏")
    bars12(d, 42, 880, 118, 30, 26, [25, 50, 80, 40, 105, 120, 95, 60, 30, 45, 70, 88], 5)
    for cx, s in [(57, "1月"), (303, "6月"), (663, "12月")]:
        d.text((cx, 900), s, font=__import__("wireframe_lib").font(19), fill=INK2, anchor="mm")
    badge(d, 676, 730, 3)

    rr(d, [36, 948, 684, 1032], r=14, fill=FILL)
    txt(d, 60, 990, "连续记录", 24, True, anchor="lm")
    for i in range(7):
        cx = 380 + i * 32
        if i < 5:
            d.ellipse([cx - 10, 980, cx + 10, 1000], fill=INK)
        else:
            d.ellipse([cx - 10, 980, cx + 10, 1000], outline=LINE, width=3)
    txt(d, 660, 990, "5 天", 26, True, anchor="rm")

    btn(d, 36, 1072, 648, 92, "生成年终食光长图", sz=28)
    badge(d, 672, 1052, 4)

    d.line([36, 1204, 684, 1204], fill=LINE, width=2)
    sec_title(d, 1234, "回忆提醒")
    badge(d, 676, 1248, 5)
    rr(d, [36, 1272, 684, 1436], r=14, fill=FILL)
    txt(d, 60, 1310, "好久没去提醒", 24, True, anchor="lm")
    switch(d, 544, 1292, 636, 1328, on=False)
    txt(d, 60, 1350, "去过 3 次以上 · 评分 4 以上 · 超过 N 天没去", 18, False, INK2)
    x = 60
    for lab in ["60 天", "90 天", "180 天"]:
        x = chip(d, x, 1376, lab, h=44, sz=20, selected=(lab == "90 天")) + 14

    rr(d, [36, 1496, 684, 1576], r=14, fill=(255, 255, 255), outline=LINE, width=2)
    txt(d, 60, 1536, "食堂 · 23 家", 24, True, anchor="lm")
    for bx, bh in [(600, 16), (616, 26), (632, 36)]:
        d.rectangle([bx, 1552 - bh, bx + 9, 1552], fill=ACCENT)
    badge(d, 664, 1472, 6)

    notes(d, 1616, [
        "① 今年 / 累计 两档切换        ③ 月度节奏 12 柱（今年 12 月）",
        "② 最爱 TOP3 = 今年次数最多，横滑查看",
        "④ 一键生成年终长图（版式见下一张）   ⑤ 回忆提醒默认关",
        "⑥ 入口：W3 列表标题行右侧新增统计图标（示意）",
    ])
    img.save(f"{OUT}/1-eats-w7-recap.png")
    print("1-eats-w7-recap.png")


# ═══════════════════ 2. eats 年度食光长图版式 ═══════════════════
def eats_longform():
    img, d = new_canvas(W, 1700)
    txt(d, 36, 44, "「年度食光」长图版式", 30, True)
    txt(d, 36, 92, "示意比例，非最终视觉；宽 1080，高度按内容增长", 20, False, INK2)
    txt(d, 36, 124, "导出：存相册 Pictures/Eats · 分享 · 年份可选今年/去年", 20, False, ACCENT)

    X0, X1 = 200, 520
    rr(d, [X0, 160, X1, 1360], r=12, fill=(255, 255, 255), outline=LINE, width=2)
    d.rectangle([X0 + 2, 340, X1 - 2, 342], fill=LINE)
    d.text((360, 230), "2026", font=__import__("wireframe_lib").font(48, True), fill=INK, anchor="mm")
    d.text((360, 290), "我的年度食光", font=__import__("wireframe_lib").font(28, True), fill=INK, anchor="mm")
    d.text((360, 326), "eats · 年度报告", font=__import__("wireframe_lib").font(16), fill=INK2, anchor="mm")

    for cx, v, l in [(262, "286", "顿"), (360, "¥12,480", "花费"), (458, "41", "家")]:
        d.text((cx, 396), v, font=__import__("wireframe_lib").font(22 if len(v) > 4 else 26, True), fill=INK, anchor="mm")
        d.text((cx, 428), l, font=__import__("wireframe_lib").font(14), fill=INK2, anchor="mm")
    d.rectangle([X0 + 2, 460, X1 - 2, 462], fill=LINE)

    d.text((360, 486), "最爱 TOP3", font=__import__("wireframe_lib").font(20, True), fill=INK, anchor="mm")
    for i, (nm, c) in enumerate([("巷子深火锅 · 23 次", ""), ("老王猪脚饭 · 12 次", ""), ("家中自做 · 9 次", "")]):
        cy = 530 + i * 52
        imgph(d, [232, cy - 20, 276, cy + 20], cross=False)
        d.text((288, cy), nm, font=__import__("wireframe_lib").font(18), fill=INK, anchor="lm")
    d.rectangle([X0 + 2, 680, X1 - 2, 682], fill=LINE)

    for x0, x1, f in [(232, 375, FILL2), (375, 452, (236, 236, 240)), (452, 488, (255, 255, 255))]:
        d.rectangle([x0, 706, x1, 734], fill=f, outline=LINE, width=1)
    d.text((303, 752), "堂食 56%", font=__import__("wireframe_lib").font(14), fill=INK2, anchor="mm")
    d.text((413, 752), "外卖 30%", font=__import__("wireframe_lib").font(14), fill=INK2, anchor="mm")
    d.rectangle([X0 + 2, 772, X1 - 2, 774], fill=LINE)

    for i, v in enumerate([25, 50, 80, 40, 105, 120, 95, 60, 30, 45, 70, 88]):
        h = v / 120 * 84
        x = 236 + i * 20.5
        d.rectangle([x, 868 - h, x + 12, 868], fill=FILL2, outline=LINE, width=1)
    d.rectangle([X0 + 2, 890, X1 - 2, 892], fill=LINE)

    d.text((360, 918), "这一年拍下的", font=__import__("wireframe_lib").font(18, True), fill=INK, anchor="mm")
    for r in range(3):
        for c in range(3):
            imgph(d, [226 + c * 92, 944 + r * 92, 226 + c * 92 + 84, 944 + r * 92 + 84], cross=False)

    d.rectangle([X0 + 2, 1236, X1 - 2, 1238], fill=LINE)
    d.text((360, 1282), "吃好喝好，来年继续", font=__import__("wireframe_lib").font(20, True), fill=INK, anchor="mm")
    d.text((360, 1320), "eats · 生成于 2026-09-20", font=__import__("wireframe_lib").font(14), fill=INK2, anchor="mm")

    anno = [(250, 1, "封面"), (400, 2, "三大数字"), (560, 3, "最爱 TOP3"),
            (720, 4, "类型占比"), (840, 5, "月度节奏"), (1085, 6, "照片墙 9 张内"),
            (1290, 7, "结尾条")]
    for ym, n, s in anno:
        d.line([X1, ym, 540, ym], fill=ACCENT, width=2)
        badge(d, 560, ym, n)
        note(d, 584, ym, s, 18, anchor="lm")

    notes(d, 1420, [
        "照片墙取今年 Visit 照片最多 9 张；空数据时按钮置灰",
        "视觉方向：奶油底 + 主题绿衬线标题 + 大数字",
        "版式 token 随迭代落 05-design-system",
    ])
    img.crop((0, 0, W, 1560)).save(f"{OUT}/2-eats-recap-longform.png")
    print("2-eats-recap-longform.png")


# ═══════════════════ 3. wardrobe 穿搭详情 · 打卡 ═══════════════════
def wd_checkin():
    img, d = new_canvas(W, 1500)
    sym_chev(d, 56, 76, 20, left=True)
    txt(d, 96, 76, "穿搭 · 2026-09-19", 34, True, anchor="lm")

    imgph(d, [36, 120, 684, 560], "成品效果图（多张横滑）")
    for i in range(3):
        cx = 344 + i * 32
        d.ellipse([cx - 6, 584, cx + 6, 596], fill=INK if i == 0 else None, outline=LINE, width=2)

    x = 36
    for lab in ["#通勤", "#早秋"]:
        x = chip(d, x, 622, lab, h=48, sz=22, selected=True) + 12

    txt(d, 36, 706, "这套包含", 26, True)
    for i, (nm) in enumerate(["上装 · 白色牛津纺衬衫", "下装 · 直筒牛仔裤"]):
        ry = 744 + i * 66
        imgph(d, [36, ry, 92, ry + 56], cross=False)
        txt(d, 108, ry + 28, nm, 22, anchor="lm")

    btn(d, 36, 900, 648, 96, "今天穿了这套", sz=28)
    badge(d, 668, 878, 1)

    rr(d, [36, 1036, 684, 1136], r=14, fill=ACC_BG, outline=ACCENT, width=2)
    sym_check(d, 70, 1086, 24, color=ACCENT)
    txt(d, 98, 1086, "今日已穿 9/20 · 再记一次（换装）/ 撤销今日", 22, True, ACCENT, anchor="lm")
    badge(d, 668, 1014, 2)

    btn(d, 36, 1196, 316, 84, "录入成品图", primary=False, sz=24)
    btn(d, 368, 1196, 316, 84, "复制长图", primary=False, sz=24)
    badge(d, 668, 1174, 3)

    notes(d, 1330, [
        "① 打卡 = 整套穿搭一次点击；成功有轻粒子反馈（阶段A 新增）",
        "② 已打卡态：同日可「再记一次」（换装）或「撤销今日」，写入 WearLog",
        "③ 底部动作区保持 it-012「滚动区 + 固定动作栏」结构不变",
    ])
    img.save(f"{OUT}/3-wardrobe-outfit-checkin.png")
    print("3-wardrobe-outfit-checkin.png")


# ═══════════════════ 4. wardrobe W9 衣橱回顾页 ═══════════════════
def wd_w9():
    img, d = new_canvas(W, 1680)
    sym_chev(d, 56, 76, 20, left=True)
    txt(d, 96, 76, "衣橱回顾 · Leo", 38, True, anchor="lm")
    toggle(d, 468, 50, ["今年", "累计"], 0)
    badge(d, 436, 76, 1)

    hero3(d, 130, 250, [("24", "单品"), ("18", "穿搭套"), ("42", "打卡次数")])

    sec_title(d, 276, "最百搭 TOP3")
    card_top3(d, 36, 1, "白色牛津纺衬衫", "进过 9 套 · 穿 11 次")
    card_top3(d, 356, 2, "小白鞋", "进过 6 套 · 穿 8 次")
    sym_chev(d, 692, 420, 18, left=False)
    badge(d, 656, 306, 2)

    sec_title(d, 556, "品类分布")
    segs = [(36, 252, FILL2), (252, 414, (236, 236, 240)), (414, 522, (255, 255, 255)),
            (522, 603, FILL2), (603, 657, (236, 236, 240)), (657, 684, (255, 255, 255))]
    for x0, x1, f in segs:
        rr(d, [x0, 600, x1, 648], r=0, fill=f, outline=LINE, width=2)
    d.text((360, 670), "上装 8 · 下装 6 · 鞋 4 · 外套 3 · 包 2 · 帽 1",
           font=__import__("wireframe_lib").font(20), fill=INK2, anchor="mm")

    rr(d, [36, 716, 684, 816], r=14, fill=FILL)
    txt(d, 60, 752, "利用率", 24, True, anchor="lm")
    rr(d, [200, 736, 560, 768], r=16, fill=(255, 255, 255), outline=LINE, width=2)
    rr(d, [200, 736, 484, 768], r=16, fill=INK)
    txt(d, 660, 752, "79%", 26, True, anchor="rm")
    txt(d, 60, 792, "穿过 ≥1 次的单品占比", 18, False, INK2)
    badge(d, 676, 700, 3)

    rr(d, [36, 840, 684, 960], r=14, fill=FILL)
    txt(d, 60, 886, "闲置清单", 24, True, anchor="lm")
    txt(d, 60, 924, "5 件从没上过身", 20, False, INK2)
    for i in range(3):
        imgph(d, [420 + i * 70, 852, 474 + i * 70, 906], cross=False)
    sym_chev(d, 652, 900, 18, left=False)
    badge(d, 676, 824, 4)

    rr(d, [36, 984, 684, 1064], r=14, fill=FILL)
    txt(d, 60, 1024, "出勤最高", 24, True, anchor="lm")
    txt(d, 260, 1024, "「通勤简约」穿过 6 次", 22, anchor="lm")

    btn(d, 36, 1104, 648, 92, "生成年度衣橱长图", sz=28)
    badge(d, 672, 1084, 5)

    d.line([36, 1236, 684, 1236], fill=LINE, width=2)
    sec_title(d, 1266, "衣柜提醒")
    badge(d, 676, 1280, 6)
    rr(d, [36, 1304, 684, 1468], r=14, fill=FILL)
    txt(d, 60, 1342, "好久没穿提醒", 24, True, anchor="lm")
    switch(d, 544, 1324, 636, 1360, on=False)
    txt(d, 60, 1382, "单品粒度 · 穿过 2 次以上 · 超过 N 天没穿 · 文案带角色名", 18, False, INK2)
    x = 60
    for lab in ["60 天", "90 天", "180 天"]:
        x = chip(d, x, 1408, lab, h=44, sz=20, selected=(lab == "90 天")) + 14

    notes(d, 1520, [
        "① 今年 / 累计 两档切换        ③ 利用率与打卡数据全部来自 WearLog（阶段A）",
        "④ 闲置清单点进二级页：缩略图网格 + 各自录入天数",
        "⑤ 一键生成年终长图（版式见下一张）   ⑥ 提醒默认关，开启时请求通知权限",
    ])
    img.save(f"{OUT}/4-wardrobe-w9-recap.png")
    print("4-wardrobe-w9-recap.png")


# ═══════════════════ 5. wardrobe 年度衣橱长图版式 ═══════════════════
def wd_longform():
    img, d = new_canvas(W, 1700)
    txt(d, 36, 44, "「年度衣橱」长图版式", 30, True)
    txt(d, 36, 92, "按当前角色出图，多角色各出各的；示意比例，非最终视觉", 20, False, INK2)
    txt(d, 36, 124, "导出：存相册 Pictures/Wardrobe · 分享 · 年份可选今年/去年", 20, False, ACCENT)

    X0, X1 = 200, 520
    rr(d, [X0, 160, X1, 1260], r=12, fill=(255, 255, 255), outline=LINE, width=2)
    d.rectangle([X0 + 2, 340, X1 - 2, 342], fill=LINE)
    sym_person(d, 360, 210, r=24)
    d.text((360, 284), "Leo 的年度衣橱", font=__import__("wireframe_lib").font(26, True), fill=INK, anchor="mm")
    d.text((360, 322), "wardrobe · 2026", font=__import__("wireframe_lib").font(16), fill=INK2, anchor="mm")

    for cx, v, l in [(262, "24", "单品"), (360, "18", "穿搭套"), (458, "42", "打卡")]:
        d.text((cx, 396), v, font=__import__("wireframe_lib").font(26, True), fill=INK, anchor="mm")
        d.text((cx, 428), l, font=__import__("wireframe_lib").font(14), fill=INK2, anchor="mm")
    d.rectangle([X0 + 2, 460, X1 - 2, 462], fill=LINE)

    d.text((360, 486), "最百搭 TOP3", font=__import__("wireframe_lib").font(20, True), fill=INK, anchor="mm")
    for i, nm in enumerate(["白衬衫 · 进过 9 套", "小白鞋 · 进过 6 套", "牛仔外套 · 进过 5 套"]):
        cy = 530 + i * 52
        imgph(d, [232, cy - 20, 276, cy + 20], cross=False)
        d.text((288, cy), nm, font=__import__("wireframe_lib").font(18), fill=INK, anchor="lm")
    d.rectangle([X0 + 2, 680, X1 - 2, 682], fill=LINE)

    for x0, x1, f in [(232, 330, FILL2), (330, 402, (236, 236, 240)), (402, 456, (255, 255, 255)),
                      (456, 488, FILL2)]:
        d.rectangle([x0, 706, x1, 734], fill=f, outline=LINE, width=1)
    d.text((360, 752), "上装 · 下装 · 鞋 · 其余", font=__import__("wireframe_lib").font(14), fill=INK2, anchor="mm")
    d.rectangle([X0 + 2, 772, X1 - 2, 774], fill=LINE)

    d.text((360, 800), "今年上身的搭配", font=__import__("wireframe_lib").font(18, True), fill=INK, anchor="mm")
    for r in range(3):
        for c in range(3):
            imgph(d, [226 + c * 92, 828 + r * 92, 226 + c * 92 + 84, 828 + r * 92 + 84], cross=False)

    d.rectangle([X0 + 2, 1120, X1 - 2, 1122], fill=LINE)
    d.text((360, 1166), "明年，更好穿搭", font=__import__("wireframe_lib").font(20, True), fill=INK, anchor="mm")
    d.text((360, 1204), "wardrobe · 生成于 2026-09-20", font=__import__("wireframe_lib").font(14), fill=INK2, anchor="mm")

    anno = [(250, 1, "封面"), (400, 2, "三大数字"), (560, 3, "最百搭 TOP3"),
            (720, 4, "品类分布"), (970, 5, "成品图墙 9 张内"), (1180, 6, "结尾条")]
    for ym, n, s in anno:
        d.line([X1, ym, 540, ym], fill=ACCENT, width=2)
        badge(d, 560, ym, n)
        note(d, 584, ym, s, 18, anchor="lm")

    notes(d, 1340, [
        "图墙取今年打卡穿搭的成品图，最多 9 张；无成品图用拼贴占位",
        "利用率 / 闲置等进 W9 回顾页；长图保持「晒」的属性",
    ])
    img.crop((0, 0, W, 1460)).save(f"{OUT}/5-wardrobe-recap-longform.png")
    print("5-wardrobe-recap-longform.png")


if __name__ == "__main__":
    os.chdir(OUT)
    eats_w7()
    eats_longform()
    wd_checkin()
    wd_w9()
    wd_longform()
    print("done ->", OUT)
