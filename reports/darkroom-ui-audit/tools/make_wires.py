#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""darkroom UI 审查 · 改版线框（4 张）。用法: python3 make_wires.py [输出目录]"""
import os
import sys

sys.path.insert(0, "/Users/leo/Documents/mini-apps/.agents/skills/ui-audit/scripts")
from wireframe_lib import (  # noqa: E402
    ACCENT, DASH, FILL, FILL2, INK, INK2, LINE, PRIMARY, W, H,
    badge, btn, chip, dashrect, font, imgph, new_canvas, note, rr, sym_camera,
    sym_check, sym_chev, txt,
)

OUT = sys.argv[1] if len(sys.argv) > 1 else "."


def switch(d, x, y, on=True):
    """开关：胶囊 + 圆头。"""
    w, h = 86, 46
    rr(d, [x, y, x + w, y + h], r=h // 2,
       fill=(180, 180, 186) if not on else PRIMARY, outline=LINE if not on else None, width=2)
    cx = (x + w - 23) if on else (x + 23)
    d.ellipse([cx - 18, y + h / 2 - 18, cx + 18, y + h / 2 + 18], fill=(255, 255, 255))


def radio(d, cx, cy, selected=False):
    d.ellipse([cx - 20, cy - 20, cx + 20, cy + 20], outline=INK2 if not selected else ACCENT, width=4)
    if selected:
        d.ellipse([cx - 11, cy - 11, cx + 11, cy + 11], fill=ACCENT)


def statusbar(d, label_top=True):
    """状态栏占位：顶区 + 时钟点。"""
    d.rectangle([0, 0, W, 96], fill=(250, 250, 252))
    d.line([0, 96, W, 96], fill=DASH, width=3)
    d.text((36, 48), "9:41", font=font(24, True), fill=INK, anchor="lm")
    d.rectangle([W - 90, 34, W - 54, 62], outline=INK2, width=3)
    if label_top:
        note(d, W - 124, 48, "状态栏安全区（insets）", 20, anchor="rm")


# ── D1 首屏/顶行 ─────────────────────────────────────────────
def wf_header(path):
    img, d = new_canvas()
    statusbar(d)
    badge(d, 336, 96, 1)
    # 顶行：品牌 + 单一设置入口（完整落在安全区之下）
    txt(d, 36, 132, "DARKROOM", 22, False, INK2)
    rr(d, [W - 106, 112, W - 34, 184], r=18, outline=LINE, width=2)
    d.line([W - 86, 136, W - 54, 136], fill=INK2, width=4)
    d.line([W - 86, 150, W - 54, 150], fill=INK2, width=4)
    d.line([W - 86, 164, W - 54, 164], fill=INK2, width=4)
    badge(d, W - 158, 148, 2)
    txt(d, 36, 216, "显影", 64, True, INK)
    txt(d, 36, 306, "把回忆洗出来——传一张图，看它像", 24, False, INK2)
    txt(d, 36, 344, "拍立得相纸一样慢慢显影。", 24, False, INK2)
    # 主/次按钮
    btn(d, 36, 416, W - 72, 96, "从相册选一张", primary=True, sz=28, check=False)
    sym_camera(d, 210, 464, 40)
    btn(d, 36, 540, W - 72, 96, "拍一张", primary=False, sz=28)
    txt(d, 36, 694, "或者，先拿示例图试试手感", 24, False, INK2)
    for i, name in enumerate(["暮色山径", "海边午后", "城市霓虹"]):
        x = 36 + i * 218
        imgph(d, [x, 750, x + 194, 950], label=name)
    # 中部原「设置」chips 删除 → footer 贴底
    dashrect(d, [36, 1010, W - 36, 1130], label="（原「设置」按钮 · 移除）")
    badge(d, 54, 1002, 3)
    note(d, 36, 1150, "设置入口收敛为顶行一处；footer 下移贴底，", 21, color=ACCENT)
    note(d, 36, 1188, "消解中部 800px 空白", 21, color=ACCENT)
    d.line([36, 1240, W - 36, 1240], fill=LINE, width=2)
    txt(d, 36, 1500, "全程离线 · 照片不上传", 24, False, INK2)
    img.save(path)
    print("saved", path)


# ── D2 显影台 ────────────────────────────────────────────────
def wf_develop(path):
    img, d = new_canvas()
    statusbar(d, label_top=False)
    sym_chev(d, 56, 140, sz=24, left=True)
    txt(d, 92, 126, "DEVELOPING", 24, True, INK2)
    txt(d, W - 36, 126, "慢洗 12s · 浮现", 24, False, INK, anchor="ra")
    # 槽口（改版：可见描边）
    rr(d, [80, 236, W - 80, 276], r=10, fill=FILL2, outline=LINE, width=2)
    txt(d, W / 2, 256, "槽口（描边加深，拟物可读）", 19, False, INK2, anchor="mm")
    # 卡片：按槽高反解宽度，完整落位，签名区全部在白框内
    cx0, cy0, cx1, cy1 = 96, 306, W - 96, 1130
    rr(d, [cx0, cy0, cx1, cy1], r=16, fill=(253, 252, 246), outline=LINE, width=2)
    badge(d, cx0 + 6, cy0 - 6, 1)
    imgph(d, [cx0 + 34, cy0 + 34, cx1 - 34, cy0 + 34 + (cx1 - cx0 - 68)], label="照片显影区")
    # 签名区（互斥两栏）
    fy = cy0 + 34 + (cx1 - cx0 - 68) + 26
    txt(d, cx0 + 40, fy, "Summer 2026", 29, False, INK)
    d.line([cx0 + 40, fy + 50, cx0 + 280, fy + 50], fill=DASH, width=2)
    txt(d, cx1 - 40, fy, "2026 09 27", 29, True, (192, 90, 26), anchor="ra")
    txt(d, cx1 - 40, fy + 74, "显影 DARKROOM", 18, False, (138, 131, 117), anchor="ra")
    badge(d, cx1 - 4, fy + 40, 2)
    note(d, cx0 + 40, fy + 74, "签名区整体收回白框内", 20)
    # 进度行（与卡脚净距）
    txt(d, 36, 1206, "浮现", 34, True, INK)
    txt(d, W - 36, 1206, "63%", 34, True, ACCENT, anchor="ra")
    badge(d, W - 116, 1174, 3)
    # 药水条
    rr(d, [36, 1290, W - 36, 1322], r=16, fill=FILL, outline=LINE, width=2)
    rr(d, [36, 1290, int((W - 72) * 0.63) + 36, 1322], r=16, fill=ACCENT)
    d.rectangle([int((W - 72) * 0.63) + 24, 1276, int((W - 72) * 0.63) + 48, 1336], fill=ACCENT)
    txt(d, W / 2, 1408, "拖动药水条可倒放重看", 24, False, INK2, anchor="mm")
    note(d, 36, 1490, "卡宽=可用槽高÷1.2，出纸/落定不越界", 21)
    img.save(path)
    print("saved", path)


# ── D3 成片页 ────────────────────────────────────────────────
def wf_result(path):
    img, d = new_canvas()
    statusbar(d, label_top=False)
    sym_chev(d, 56, 140, sz=24, left=True)
    txt(d, 92, 126, "成片", 30, True, INK)
    txt(d, W - 36, 132, "PRINTED", 22, False, INK2, anchor="ra")
    # 卡片（紧凑：宽 520 居中）
    cx0, cy0, cx1 = 100, 200, 620
    photo = 468
    pt, pb = cy0 + 26, cy0 + 26 + photo
    cy1 = pb + 172
    rr(d, [cx0, cy0, cx1, cy1], r=16, fill=(253, 252, 246), outline=LINE, width=2)
    imgph(d, [cx0 + 26, pt, cx1 - 26, pb])
    fy = pb + 30
    txt(d, cx0 + 26, fy, "Summer 2026", 30, False, INK)
    txt(d, cx1 - 26, fy, "2026 09 27", 30, True, (192, 90, 26), anchor="ra")
    badge(d, cx1 + 4, fy + 2, 1)
    d.line([cx0 + 270, fy - 6, cx0 + 270, fy + 50], fill=ACCENT, width=3)
    note(d, cx0 + 26, fy + 66, "标题域止于 52% · 日期章左边界夹断", 20)
    txt(d, cx1 - 26, fy + 110, "显影 DARKROOM", 18, False, (138, 131, 117), anchor="ra")
    # 编辑卡
    ey = cy1 + 22
    rr(d, [36, ey, W - 36, ey + 258], r=24, fill=(245, 245, 247), outline=LINE, width=2)
    rr(d, [66, ey + 30, W - 66, ey + 102], r=14, outline=(150, 150, 156), width=2)
    txt(d, 92, ey + 66, "手写标题（可空）", 24, False, INK2, anchor="lm")
    rr(d, [66, ey + 122, W - 66, ey + 194], r=14, outline=(150, 150, 156), width=2)
    txt(d, 92, ey + 158, "2026 09 27", 24, False, INK, anchor="lm")
    txt(d, 66, ey + 224, "卡脚水印", 24, False, INK, anchor="lm")
    switch(d, W - 152, ey + 202, on=True)
    # 规格
    gy = ey + 282
    txt(d, 36, gy, "视频规格", 22, False, INK2)
    chip(d, 36, gy + 40, "方形 1080×1080", w=300, h=60, selected=True, check=True, sz=22)
    chip(d, 348, gy + 40, "竖版 1080×1350", w=300, h=60, sz=22)
    # 进度条贴 CTA 上方
    py = gy + 126
    dashrect(d, [36, py, W - 36, py + 44], label="冲洗中 42%（进度随按钮入首屏）", sz=20)
    badge(d, 48, py - 8, 2)
    # CTA 行（首屏内完整可见）
    cy = py + 70
    btn(d, 36, cy, 306, 90, "存图片", primary=True, sz=27)
    btn(d, 378, cy, 306, 90, "存视频", primary=True, sz=27)
    badge(d, 48, cy - 8, 3)
    # 次级行（三钮一行）
    sy = cy + 116
    btn(d, 36, sy, 200, 66, "分享图片", primary=False, sz=21)
    btn(d, 260, sy, 200, 66, "分享视频", primary=False, sz=21)
    btn(d, 484, sy, 200, 66, "再洗一张", primary=False, sz=21)
    img.save(path)
    print("saved", path)


# ── D4 速度档 ────────────────────────────────────────────────
def wf_speed(path):
    img, d = new_canvas()
    txt(d, 36, 48, "速度档 · 选档即生效", 36, True, INK)
    # 设置卡片
    rr(d, [36, 130, W - 36, 660], r=24, fill=(245, 245, 247), outline=LINE, width=2)
    txt(d, 66, 170, "显影速度", 24, False, INK2)
    opts = [("慢洗", "12s", True), ("标准", "8s", False), ("快显", "4s", False)]
    for i, (name, sec, sel) in enumerate(opts):
        y = 250 + i * 130
        radio(d, 110, y, selected=sel)
        txt(d, 166, y, name, 30, False, INK, anchor="lm")
        txt(d, W - 66, y, sec, 26, False, INK2, anchor="rm")
        if sel:
            badge(d, 66, y - 36, 1)
    note(d, 110, 610, "DataStore 写入 develop_speed=SLOW", 21)
    # 绑定箭头
    d.line([W / 2, 700, W / 2, 790], fill=ACCENT, width=5)
    d.polygon([(W / 2 - 14, 776), (W / 2 + 14, 776), (W / 2, 804)], fill=ACCENT)
    txt(d, W / 2 + 30, 744, "collect 即重建 clock", 22, False, ACCENT, anchor="lm")
    # 显影台 header 联动
    rr(d, [36, 830, W - 36, 1050], r=24, fill=FILL, outline=LINE, width=2)
    txt(d, 66, 874, "DEVELOPING", 24, True, INK2)
    txt(d, W - 66, 874, "慢洗 12s · 浮现", 26, False, INK, anchor="rm")
    badge(d, W - 46, 846, 2)
    # 进度条示意
    rr(d, [66, 940, W - 66, 972], r=16, fill=FILL2, outline=LINE, width=2)
    rr(d, [66, 940, int((W - 132) * 0.42) + 66, 972], r=16, fill=ACCENT)
    txt(d, 66, 1000, "实测走完 12s，与标签一致", 22, False, INK2)
    note(d, 36, 1120, "改法：DevelopSpeed.collect → 每次显影前", 22)
    note(d, 36, 1160, "clock = DevelopClock(speed.durationMs)", 22)
    note(d, 36, 1240, "现状：clock 固定 STANDARD(8s)——", 22)
    note(d, 36, 1280, "落盘 SLOW 后实测仍 8.8s（仅标签变化）", 22)
    img.save(path)
    print("saved", path)


if __name__ == "__main__":
    wf_header(os.path.join(OUT, "wf-header.png"))
    wf_develop(os.path.join(OUT, "wf-develop.png"))
    wf_result(os.path.join(OUT, "wf-result.png"))
    wf_speed(os.path.join(OUT, "wf-speed.png"))
