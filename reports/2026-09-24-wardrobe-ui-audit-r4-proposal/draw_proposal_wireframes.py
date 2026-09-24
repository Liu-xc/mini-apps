#!/usr/bin/env python3
"""Recreate lightweight graybox proposals for wardrobe UI issues C1-C10."""
from pathlib import Path
import sys

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[2]
ASSETS = Path(__file__).resolve().parent / "assets"
sys.path.insert(0, str(ROOT / ".agents/skills/ui-audit/scripts"))
import wireframe_lib as wf  # noqa: E402


def screen_base(title=None):
    img, d = wf.new_canvas()
    # Status bar and safe-area header are intentionally compact and neutral.
    d.rectangle([0, 0, wf.W, 68], fill=(248, 248, 250))
    if title:
        wf.txt(d, 36, 82, title, 38, True)
    return img, d


def bottom_nav_proposed(d, active=0):
    y = 1492
    d.line([0, y, wf.W, y], fill=wf.LINE, width=2)
    d.rectangle([0, y, wf.W, wf.H], fill=(250, 250, 252))
    for i, label in enumerate(["搭配", "穿搭记录", "衣橱"]):
        cx = wf.W * (i + .5) / 3
        if i == active:
            wf.rr(d, [cx - 42, y + 12, cx + 42, y + 56], r=22, fill=wf.FILL2)
        d.rounded_rectangle([cx - 12, y + 22, cx + 12, y + 46], radius=6,
                            outline=wf.INK if i == active else wf.INK2, width=3)
        wf.txt(d, cx, y + 72, label, 19, fill=wf.INK2, anchor="mm")


def closet():
    img, d = screen_base()
    wf.txt(d, 36, 70, "衣橱 · Leo", 38, True)
    wf.txt(d, 350, 81, "共20件", 22, fill=wf.INK2)
    wf.txt(d, 514, 82, "回顾   心愿", 21, fill=wf.INK2)

    chips = [("全部", 92, True), ("上装", 92, False), ("外套", 92, False),
             ("下装", 92, False), ("筛选", 92, False)]
    x = 36
    for label, width, selected in chips:
        wf.chip(d, x, 146, label, w=width, h=54, selected=selected, sz=22)
        x += width + 8

    cards = [
        (36, 232, "浅蓝色亚麻衬衫", "浅蓝色   #休闲  #度假"),
        (370, 232, "黑色罗纹高领衫", "黑色   #通勤  #简约"),
        (36, 782, "奶油色针织开衫", "奶油色   #简约  #居家"),
        (370, 782, "海军条纹针织 Polo", "米白色   #休闲  #复古"),
    ]
    for x, y, name, meta in cards:
        wf.rr(d, [x, y, x + 314, y + 508], r=18, fill=(255, 255, 255), outline=wf.LINE, width=2)
        wf.imgph(d, [x + 14, y + 14, x + 300, y + 354], "单品照片", cross=False, r=12)
        wf.txt(d, x + 14, y + 371, name, 25, True)
        wf.txt(d, x + 14, y + 416, meta, 18, fill=wf.INK2)

    # Fixed action row owns its layout height; it never floats over card content.
    d.rectangle([0, 1340, wf.W, 1492], fill=(250, 250, 252))
    d.line([0, 1340, wf.W, 1340], fill=wf.LINE, width=2)
    wf.badge(d, 40, 1414, 2)
    wf.btn(d, 64, 1360, 592, 76, "＋ 添加衣物", primary=True, sz=27)

    # Selected icon/pill is the only state decoration; tab labels share one tone.
    y = 1492
    d.line([0, y, wf.W, y], fill=wf.LINE, width=2)
    d.rectangle([0, y, wf.W, wf.H], fill=(250, 250, 252))
    labels = ["搭配", "穿搭记录", "衣橱"]
    for i, label in enumerate(labels):
        cx = wf.W * (i + .5) / 3
        if i == 2:
            wf.rr(d, [cx - 43, y + 12, cx + 43, y + 56], r=22, fill=wf.FILL2)
        d.rounded_rectangle([cx - 13, y + 22, cx + 13, y + 46], radius=6,
                            outline=wf.INK if i == 2 else wf.INK2, width=3)
        wf.txt(d, cx, y + 72, label, 19, fill=wf.INK2, anchor="mm")
    wf.note(d, 60, 1320, "① pill + 图标；Tab 名称同色", 18)
    return img


def roles():
    img, d = screen_base("搭配")
    # Dimmed background suggests the existing bottom sheet without recreating details.
    d.rectangle([0, 68, wf.W, 930], fill=(242, 242, 245))
    wf.imgph(d, [220, 190, 500, 570], "当前搭配", cross=False)
    d.rectangle([0, 930, wf.W, wf.H], fill=(255, 255, 255))
    wf.rr(d, [318, 954, 402, 964], r=5, fill=wf.LINE)
    wf.txt(d, 40, 1000, "切换衣橱", 36, True)

    wf.badge(d, 40, 1113, 1)
    wf.rr(d, [64, 1062, 668, 1164], r=18, fill=wf.ACC_BG)
    wf.txt(d, 92, 1085, "Leo", 30, True)
    wf.txt(d, 218, 1090, "使用中", 22, fill=wf.INK)
    wf.rr(d, [64, 1182, 668, 1284], r=18, outline=wf.LINE, fill=(255, 255, 255))
    wf.txt(d, 92, 1206, "Mia", 30)
    wf.rr(d, [52, 1322, 536, 1404], r=40, fill=wf.PRIMARY)
    wf.txt(d, 294, 1340, "＋ 新建角色", 25, True, fill=(255, 255, 255), anchor="mm")
    wf.txt(d, 594, 1340, "管理", 23, fill=wf.INK2, anchor="mm")
    wf.note(d, 56, 1440, "当前态 = 浅底 + 状态文字", 20)
    return img


def namebar():
    img, d = screen_base("搭配 · Leo")
    wf.imgph(d, [86, 188, 634, 800], "槽位照片", cross=False, r=18)
    wf.rr(d, [86, 800, 634, 884], r=0, fill=wf.PRIMARY)
    wf.txt(d, 108, 822, "黑色罗纹高…", 25, True, fill=(255, 255, 255))
    wf.txt(d, 483, 825, "3/5", 23, fill=(255, 255, 255))
    d.line([588, 830, 612, 854], fill=(255, 255, 255), width=4)
    d.line([612, 830, 588, 854], fill=(255, 255, 255), width=4)
    wf.badge(d, 40, 938, 3)
    wf.note(d, 76, 922, "单行省略，读屏保留完整名称", 19)
    wf.btn(d, 58, 1030, 604, 76, "复制长图", primary=True, sz=26)
    bottom_nav_proposed(d, active=0)
    return img


def wish_form():
    img, d = screen_base("心愿")
    wf.txt(d, 36, 146, "种草一件", 36, True)
    wf.imgph(d, [36, 202, 164, 292], "商品图", cross=False, r=12)
    wf.rr(d, [184, 202, 684, 274], r=13, outline=wf.LINE, width=2)
    wf.txt(d, 204, 222, "名称（必填）", 22, fill=wf.INK2)
    wf.rr(d, [184, 294, 684, 366], r=13, outline=wf.LINE, width=2)
    wf.txt(d, 204, 314, "价格（可选）", 22, fill=wf.INK2)

    wf.txt(d, 36, 400, "品类（必选）", 21, fill=wf.INK2)
    x = 36
    for label, width, selected in [("上装", 88, True), ("外套", 88, False), ("下装", 88, False),
                                   ("连衣裙", 112, False), ("鞋", 68, False), ("包", 68, False)]:
        wf.chip(d, x, 438, label, w=width, h=54, selected=selected, sz=20)
        x += width + 8

    wf.rr(d, [36, 516, 330, 586], r=13, outline=wf.LINE, width=2)
    wf.txt(d, 56, 536, "颜色（可选）", 20, fill=wf.INK2)
    wf.rr(d, [350, 516, 684, 586], r=13, outline=wf.LINE, width=2)
    wf.txt(d, 370, 536, "商品链接（可选）", 20, fill=wf.INK2)
    wf.rr(d, [36, 602, 684, 674], r=13, outline=wf.LINE, width=2)
    wf.txt(d, 56, 622, "描述（可选）", 20, fill=wf.INK2)

    wf.txt(d, 36, 708, "标签", 21, fill=wf.INK2)
    x = 36
    for label in ["通勤", "休闲", "运动", "约会", "度假", "正式", "简约"]:
        x = wf.chip(d, x, 744, label, h=50, sz=19) + 8
    # Right-edge wash hints at horizontal overflow.
    for i in range(32):
        shade = 255 - int(13 * (i / 31))
        d.line([650 + i, 736, 650 + i, 806], fill=(shade, shade, shade), width=1)

    wf.rr(d, [36, 840, 684, 904], r=13, outline=wf.LINE, width=2)
    wf.txt(d, 56, 858, "输入自定义标签", 19, fill=wf.INK2)
    # Fixed save action remains reachable above the system navigation area.
    d.rectangle([0, 1408, wf.W, wf.H], fill=(250, 250, 252))
    d.line([0, 1408, wf.W, 1408], fill=wf.LINE, width=2)
    wf.rr(d, [36, 1430, 684, 1512], r=41, fill=wf.FILL2)
    wf.txt(d, 360, 1454, "收进想买", 26, True, fill=wf.INK2, anchor="mm")
    return img


def main():
    ASSETS.mkdir(parents=True, exist_ok=True)
    pages = [
        ("wf-proposal-W3-closet.png", closet()),
        ("wf-proposal-W2-roles.png", roles()),
        ("wf-proposal-W1-namebar.png", namebar()),
        ("wf-proposal-W10-form.png", wish_form()),
    ]
    for name, img in pages:
        img.save(ASSETS / name)

    thumb_w = 240
    thumb_h = round(thumb_w * wf.H / wf.W)
    sheet = Image.new("RGB", (thumb_w * 2, thumb_h * 2), (235, 235, 238))
    for i, (name, img) in enumerate(pages):
        x = (i % 2) * thumb_w
        y = (i // 2) * thumb_h
        sheet.paste(img.resize((thumb_w, thumb_h)), (x, y))
    sheet.save(ASSETS / "wf-proposal-contact-sheet.png")

    comparisons = [
        ("W3 · 衣橱", "current-W3-filtered.png", "wf-proposal-W3-closet.png"),
        ("W2 · 角色", "current-W2-roles.png", "wf-proposal-W2-roles.png"),
        ("W1 · 搭配名称栏", "current-W1-match.png", "wf-proposal-W1-namebar.png"),
        ("W10 · 心愿表单", "current-W10-form.png", "wf-proposal-W10-form.png"),
    ]
    pair_w, pair_h, header_h = 1480, 1650, 50
    pair_images = []
    for title, current_name, proposal_name in comparisons:
        pair = Image.new("RGB", (pair_w, pair_h), (242, 242, 245))
        pd = ImageDraw.Draw(pair)
        pd.text((20, 8), f"{title} · 现状（最新模拟器截图）", font=wf.font(21), fill=wf.INK)
        pd.text((760, 8), f"{title} · 提案（灰盒）", font=wf.font(21), fill=wf.INK)
        current = Image.open(ASSETS / current_name).convert("RGB").resize((720, 1600))
        proposal = Image.open(ASSETS / proposal_name).convert("RGB")
        pair.paste(current, (0, header_h))
        pair.paste(proposal, (760, header_h))
        pair_images.append(pair)
        pair.save(ASSETS / f"compare-{title.split(' ')[0]}-current-proposal.png")

    comparison_sheet = Image.new("RGB", (pair_w * 2, pair_h * 2), (230, 230, 234))
    for i, pair in enumerate(pair_images):
        x = (i % 2) * pair_w
        y = (i // 2) * pair_h
        comparison_sheet.paste(pair, (x, y))
    comparison_sheet.save(ASSETS / "wf-current-vs-proposal-contact-sheet.png")


if __name__ == "__main__":
    main()
