#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""r3 报告资产：灰盒改版线框 ×4 + 对比度修法图 ×1 + 截图拷入 assets/。
依赖 ui-audit skill 的 wireframe_lib（符号全部自绘，无 tofu）。"""
import os
import shutil
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
SKILL = "/Users/leo/Documents/mini-apps/.agents/skills/ui-audit/scripts"
sys.path.insert(0, SKILL)

from wireframe_lib import (  # noqa: E402
    ACC_BG, ACCENT, DASH, FILL, FILL2, INK, INK2, LINE, PRIMARY, W, H,
    badge, bottom_nav, btn, chip, dashrect, font, imgph, new_canvas, note,
    rr, sym_person, sym_star_row, sym_x, txt,
)

ASSETS = os.path.join(HERE, "assets")
SHOTS = os.path.join(HERE, "shots")
os.makedirs(ASSETS, exist_ok=True)


def shot(name):
    shutil.copy(os.path.join(SHOTS, name), os.path.join(ASSETS, name))


# ---------- 1) wf-closet：W3 FAB 避让 ----------
def wf_closet():
    img, d = new_canvas()
    txt(d, 36, 44, "衣橱 · Leo", 40, True)
    txt(d, W - 220, 52, "共 20 件", 24, fill=INK2)
    x = 36
    for lb, sel in [("全部", True), ("上装", False), ("外套", False), ("下装", False)]:
        w = 96 if lb == "全部" else 84
        chip(d, x, 116, lb, w=w, h=64, selected=sel, check=sel)
        x += w + 14
    chip(d, W - 150, 116, "筛选", w=114, h=64)
    # 两列卡片（第二行右侧卡被 FAB 区域影响——改版后 meta 行完整）
    for r in range(2):
        for c in range(2):
            x0 = 36 + c * 330
            y0 = 220 + r * 560
            rr(d, [x0, y0, x0 + 318, y0 + 530], r=16, fill=(255, 255, 255), outline=LINE, width=2)
            imgph(d, [x0 + 16, y0 + 16, x0 + 302, y0 + 400], "照片")
            rr(d, [x0 + 246, y0 + 28, x0 + 302, y0 + 84], r=28, fill=FILL2)
            d.text((x0 + 274, y0 + 56), "⋮", font=font(26, True), fill=INK, anchor="mm")
            txt(d, x0 + 16, y0 + 420, "海军条纹针织 Polo", 26, True)
            txt(d, x0 + 16, y0 + 470, "米白色  #休闲 #复古", 21, fill=INK2)
    # ① FAB 让位：末行 meta 上方预留，FAB 悬于卡片 meta 之外
    fab = [W - 36 - 108, 1256, W - 36, 1364]
    rr(d, fab, r=54, fill=(52, 138, 96))
    d.text(((fab[0] + fab[2]) // 2, (fab[1] + fab[3]) // 2 + 2), "+", font=font(46, True),
           fill=(255, 255, 255), anchor="mm")
    badge(d, fab[0], fab[3], 1)
    note(d, 36, 1380, "① 网格 contentPaddingBottom 让出 FAB 足迹：", 21)
    note(d, 36, 1414, "末行 meta 不进入 FAB 半径，#复古 全字可见", 21)
    bottom_nav(d, ["搭配", "穿搭记录", "衣橱"], active=2)
    img.save(os.path.join(ASSETS, "wf-closet.png"))


# ---------- 2) wf-nav：底部导航双通道 ----------
def wf_nav():
    img, d = new_canvas()
    txt(d, 36, 44, "穿搭记录 · Leo", 40, True)
    chip(d, W - 240, 44, "随机翻一套", w=200, h=64)
    for i, t in enumerate(["全部", "#通勤", "#休闲", "#度假"]):
        chip(d, 36 + i * 150, 130, t, w=134, h=64, selected=(i == 0), check=(i == 0))
    rr(d, [36, 230, W - 36, 760], r=20, fill=(255, 255, 255), outline=LINE, width=2)
    imgph(d, [56, 250, W - 56, 740], "成品图拼贴")
    # 分页胶囊（现状已在卡下方）
    from wireframe_lib import pager_pill
    pager_pill(d, W / 2, 820, 1, 5)
    rr(d, [36, 880, W - 36, 1400], r=16, fill=(255, 255, 255), outline=LINE, width=2)
    txt(d, 56, 900, "全部 5 套", 26, True)
    for r in range(2):
        for c in range(2):
            imgph(d, [56 + c * 320, 950 + r * 220, 56 + c * 320 + 300, 950 + r * 220 + 200], "缩略")
    # 自绘修正导航：pill+图标（双通道），文字一律 ink 弱化
    y = H - 108
    d.line([0, y, W, y], fill=LINE, width=2)
    d.rectangle([0, y, W, H], fill=(250, 250, 252))
    labels = ["搭配", "穿搭记录", "衣橱"]
    for i, lb in enumerate(labels):
        cx = W * (i + 0.5) / len(labels)
        if i == 1:
            rr(d, [cx - 44, y + 14, cx + 44, y + 58], r=22, fill=FILL2)
        rr(d, [cx - 15, y + 18, cx + 15, y + 48], r=8,
           fill=INK if i == 1 else None, outline=LINE if i != 1 else None, width=2)
        d.text((cx, y + 78), lb, font=font(19), fill=INK if i == 1 else INK2, anchor="mm")
    badge(d, W * 1.5 / 3 + 66, y + 36, 1)
    badge(d, W * 1.5 / 3 - 88, y + 78, 2)
    note(d, 36, y - 76, "① 选中= pill+图标 变化（图形通道）  ② 文字三档同色，", 21)
    note(d, 36, y - 42, "不再随选中变色 —— 语义只由两个通道承载", 21)
    img.save(os.path.join(ASSETS, "wf-nav.png"))


# ---------- 3) wf-roles：角色弹层单通道 ----------
def wf_roles():
    img, d = new_canvas()
    txt(d, 36, 44, "（背景压暗的 W1）", 24, fill=INK2)
    imgph(d, [36, 96, W - 36, 620], "")
    rr(d, [246, 348, 474, 394], r=10, fill=(255, 255, 255), outline=LINE, width=2)
    txt(d, 260, 358, "搭配页 · scrim 压暗", 24, fill=INK2)
    sheet_y = 660
    rr(d, [0, sheet_y, W, H], r=28, fill=(255, 255, 255))
    rr(d, [W / 2 - 40, sheet_y + 18, W / 2 + 40, sheet_y + 32], r=7, fill=LINE)
    txt(d, 36, sheet_y + 66, "切换衣橱", 36, True)
    # Leo 选中行：色块+文字 only（去 ✓）
    rr(d, [20, sheet_y + 140, W - 20, sheet_y + 236], r=24, fill=ACC_BG)
    sym_person(d, 76, sheet_y + 188, r=22)
    txt(d, 124, sheet_y + 166, "Leo", 30, True)
    txt(d, 124, sheet_y + 204, "使用中", 22, fill=INK2)
    badge(d, W - 60, sheet_y + 150, 1)
    sym_person(d, 76, sheet_y + 300, r=22)
    txt(d, 124, sheet_y + 280, "Mia", 30, True)
    d.line([36, sheet_y + 360, W - 36, sheet_y + 360], fill=LINE, width=2)
    btn(d, 36, sheet_y + 400, W - 72 - 160, 88, "＋ 新建角色")
    btn(d, W - 36 - 150, sheet_y + 400, 150, 88, "管理", primary=False)
    note(d, 36, sheet_y + 530, "① 选中态只保留两通道：浅绿色块 + 「使用中」文字，", 21)
    note(d, 36, sheet_y + 564, "删除行内对勾图标（图标+文字+色块三重编码违 §5.2）", 21)
    note(d, 36, sheet_y + 630, "使用中行整行可点（411×48dp 实测达标）；点 scrim 关闭", 21, color=INK2)
    rr(d, [W / 2 - 70, 1548, W / 2 + 70, 1562], r=7, fill=LINE)
    img.save(os.path.join(ASSETS, "wf-roles.png"))


# ---------- 4) wf-namebar：W1 名称条单行 ----------
def wf_namebar():
    img, d = new_canvas()
    txt(d, 36, 44, "Leo", 40, True)
    sym_star_row(d, W - 210, 60, filled=0, total=1)
    txt(d, W - 178, 46, "混入心愿", 24, fill=INK2)
    # 上身三格（放大示意）
    for i, (nm, cnt) in enumerate([("浅蓝色牛仔夹克…", "4/4"), ("浅灰色连帽卫衣", "5/5"), ("奶油色针织开衫", "2/2")]):
        x0 = 36 + i * 218
        rr(d, [x0, 130, x0 + 206, 460], r=14, fill=(255, 255, 255), outline=LINE, width=2)
        imgph(d, [x0 + 10, 140, x0 + 196, 380], "照片")
        rr(d, [x0, 392, x0 + 206, 460], r=10, fill=(58, 58, 58))
        d.text((x0 + 12, 410), nm, font=font(20, True), fill=(255, 255, 255), anchor="la")
        d.text((x0 + 158, 436), cnt, font=font(18), fill=(235, 235, 235), anchor="mm")
        d.text((x0 + 190, 436), "×", font=font(22), fill=(255, 255, 255), anchor="mm")
    badge(d, 244, 400, 1)
    badge(d, 462, 436, 2)
    # 对照：错误两行样例（红叉）画在下方
    rr(d, [36, 520, 360, 660], r=14, fill=(255, 255, 255), outline=LINE, width=2)
    rr(d, [36, 576, 360, 660], r=10, fill=(58, 58, 58))
    d.text((48, 586), "浅蓝色牛仔夹", font=font(20, True), fill=(255, 255, 255), anchor="la")
    d.text((48, 618), "克        4/4 ×", font=font(20, True), fill=(255, 255, 255), anchor="la")
    sym_x(d, 340, 546, sz=16)
    txt(d, 396, 540, "现状：孤字折行", 24, fill=(245, 63, 63))
    note(d, 36, 720, "① 名称单行省略：字号三段式压到最小仍超宽 → 尾部 …，", 21)
    note(d, 36, 754, "禁止折出孤字行（夹/克、卫/衣）", 21)
    note(d, 36, 800, "② 计数提纯白（≥4.5:1），与关闭钮一并右对齐不参与换行", 21)
    dashrect(d, [36, 870, W - 36, 1010], label="其余格位同规则", label_color=INK2)
    # 应用示例：两张修正后的格位
    for i, (nm, cnt) in enumerate([("深蓝色牛仔 A 字裙…", "1/2"), ("鼠尾草绿尼龙双肩包…", "2/2")]):
        x0 = 36 + i * 330
        rr(d, [x0, 1060, x0 + 318, 1330], r=14, fill=(255, 255, 255), outline=LINE, width=2)
        imgph(d, [x0 + 10, 1070, x0 + 308, 1250], "照片")
        rr(d, [x0, 1262, x0 + 318, 1330], r=10, fill=(58, 58, 58))
        d.text((x0 + 12, 1280), nm, font=font(22, True), fill=(255, 255, 255), anchor="la")
        d.text((x0 + 258, 1306), cnt, font=font(20), fill=(255, 255, 255), anchor="mm")
        d.text((x0 + 300, 1306), "×", font=font(24), fill=(255, 255, 255), anchor="mm")
    note(d, 36, 1370, "最小字号档仍超宽 → 单行省略，绝不折出孤字", 21)
    bottom_nav(d, ["搭配", "穿搭记录", "衣橱"], active=0)
    img.save(os.path.join(ASSETS, "wf-namebar.png"))


# ---------- 5) fig-contrast：accent 色阶修法 ----------
def fig_contrast():
    img, d = new_canvas()
    txt(d, 36, 44, "对比度修法 · accent 色阶", 36, True)
    note(d, 36, 100, "白字 / accent 基线 ≥ 4.5:1（DESIGN.md §2.2）", 22, color=INK)

    def sample(y, title, bg, fg, ratio, verdict):
        txt(d, 36, y, title, 24, True, fill=INK)
        rr(d, [36, y + 40, 420, y + 140], r=16, fill=bg)
        d.text((228, y + 90), "复制长图", font=font(34, True), fill=fg, anchor="mm")
        txt(d, 460, y + 52, ratio, 34, True, fill=(245, 63, 63) if verdict == "×" else (11, 138, 76))
        txt(d, 460, y + 100, verdict, 26, fill=INK2)

    sample(160, "现状：#429E68 实底 + 白字", (66, 158, 104), (245, 249, 243), "3.12 : 1", "× 低于 4.5")
    sample(340, "修法：#1D6845 深阶 + 白字", (29, 104, 69), (245, 249, 243), "6.70 : 1", "达标 · 仓内已有")
    badge(d, 240, 172, 1)

    txt(d, 36, 540, "accent 作字色（随机一套 / 导航选中 / 价格）", 24, True, fill=INK)
    rr(d, [36, 584, 420, 664], r=14, fill=(245, 249, 243))
    d.text((228, 624), "随机一套", font=font(30, True), fill=(66, 158, 104), anchor="mm")
    txt(d, 460, 596, "3.12 : 1 ×", 30, True, fill=(245, 63, 63))
    rr(d, [36, 688, 420, 768], r=14, fill=(245, 249, 243))
    d.text((228, 728), "随机一套", font=font(30, True), fill=(29, 104, 69), anchor="mm")
    txt(d, 460, 700, "6.70 : 1 达标", 30, True, fill=(11, 138, 76))
    badge(d, 404, 600, 2)

    txt(d, 36, 830, "信息弱化文本下限：inkFaint ≥ 3:1", 24, True, fill=INK)
    rows = [("「演示模式」角标（1.71:1 ×）", "→ 提至 #808D82 级（3.3:1 达标）"),
            ("名称条计数 3.74:1 ×", "→ 纯白（≥4.5 达标）"),
            ("「使用中」2.79:1 ×（连 3 都不到）", "→ 深绿文字（≥4.5 达标）")]
    y = 880
    for bad, good in rows:
        txt(d, 36, y, bad, 23, fill=(245, 63, 63))
        txt(d, 36, y + 34, good, 23, fill=(11, 138, 76))
        y += 84
    badge(d, 470, 892, 3)
    note(d, 36, 1180, "① 白字按钮底色切 #1D6845 深阶  ② accent 作字改深阶", 21)
    note(d, 36, 1214, "③ 弱化文本逐项提到 token 下限（角标/计数/使用中）", 21)
    dashrect(d, [36, 1270, W - 36, 1400], label="token 层一次修，全站按钮同步生效", label_color=INK2)
    img.save(os.path.join(ASSETS, "fig-contrast.png"))


if __name__ == "__main__":
    wf_closet()
    wf_nav()
    wf_roles()
    wf_namebar()
    fig_contrast()
    for f in os.listdir(SHOTS):
        if f.endswith(".png"):
            shot(f)
    print("assets:", len(os.listdir(ASSETS)))
