#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""validate_assets.py — 演示素材（assets/mock）入库校验（it-035）。

检查项
  1. item-*.png 宽高比/尺寸一致性：全部单品必须同规格（2026-09-24 走查发现
     01~24=362×362、25~36=410×319 两批混用，是卡片图片适配不统一的根因）。
  2. 四角背景纯净度：stddev 超阈值 → 告警（背景带场景/杂物）。
  3. 底缘残片风险：单品图底边应为纯背景，若底部数行出现与背景强对比的
     异色像素（相邻衣物入镜残片），点名报出。已知历史问题：item-03/04/16。
  4. effect-*.png 尺寸一致性；主体安全边距属视觉语义，脚本不猜，
     在输出中提示走查/视觉抽检覆盖（已知：effect-commute 底部留白 <3%）。

用法
  python3 validate_assets.py [--assets DIR] [--strict]

  默认：打印报告，FAIL 也 exit 0（当前存量未重出，允许带病运行做基线记录）。
  --strict：存在 FAIL 时 exit 1（供 CI / pre-commit 挂钩）。
"""
import argparse
import glob
import os
import sys
from collections import defaultdict

from PIL import Image, ImageStat

CORNER = 40          # 四角采样边长 px
CORNER_STD_MAX = 12  # 背景纯净度 stddev 阈值
BOTTOM_ROWS = 6      # 底缘检测行数
BG_DELTA = 40        # 与背景均值的通道差阈值
BG_DIRTY_FRAC = 0.05 # 底缘异色像素占比阈值
# 视觉实锤的底缘残片素材（走查点名 03/04/16 + 2026-09-24 条带复核 06/19/20）；
# 成因=裁剪窗口跨进源图相邻衣物；重出图净化后从清单移除
KNOWN_DEBRIS = {"item-03.png", "item-04.png", "item-16.png",
                "item-06.png", "item-19.png", "item-20.png"}


def corners_bg(im):
    """四角采样 → (各角 stddev 列表, 背景均值 RGB)。"""
    w, h = im.size
    patches = [
        im.crop((0, 0, CORNER, CORNER)),
        im.crop((w - CORNER, 0, w, CORNER)),
        im.crop((0, h - CORNER, CORNER, h)),
        im.crop((w - CORNER, h - CORNER, w, h)),
    ]
    stats = [ImageStat.Stat(p) for p in patches]
    stds = [max(s.stddev) for s in stats]
    mean = tuple(sum(s.mean[c] for s in stats) / (4 * 3) if False else
                 sum(s.mean[c] for s in stats) / 4 for c in range(3))
    return stds, mean


def bottom_debris(im, bg_mean):
    """底缘 BOTTOM_ROWS 行中与背景强异色的像素占比，及异色像素的着色分类。

    分类（信息性证据，不作最终判决）：
      chromatic  异色像素饱和度高（max-min>25）且与画面中心本品主色距离大
                 → 疑「邻物残片」
      neutral    中性灰（饱和度低）→ 疑投影/本品暗部
      garment    与本品主色接近 → 本品衣摆自然贴底
    返回 (占比, class, chromatic占比)。
    """
    w, h = im.size
    # 本品主色：中心 40% 区域的均值
    cw, ch = int(w * 0.4), int(h * 0.4)
    core = ImageStat.Stat(im.crop((w // 2 - cw // 2, h // 2 - ch // 2,
                                   w // 2 + cw // 2, h // 2 + ch // 2)))
    garment = core.mean
    band = im.crop((0, h - BOTTOM_ROWS, w, h)).convert("RGB")
    px = band.load()
    dirty = chrom = near_garment = 0
    total = 0
    for y in range(BOTTOM_ROWS):
        for x in range(0, w, 2):  # 隔列采样足够
            total += 1
            r, g, b = px[x, y]
            delta = (abs(r - bg_mean[0]) + abs(g - bg_mean[1]) + abs(b - bg_mean[2])) / 3
            if delta <= BG_DELTA:
                continue
            dirty += 1
            sat = max(r, g, b) - min(r, g, b)
            gd = (abs(r - garment[0]) + abs(g - garment[1]) + abs(b - garment[2])) / 3
            if gd < 45:
                near_garment += 1
            elif sat > 25:
                chrom += 1
    frac = dirty / max(total, 1)
    if dirty == 0:
        cls = "clean"
    elif near_garment / dirty >= 0.6:
        cls = "garment"
    elif chrom / dirty >= 0.4:
        cls = "chromatic"
    else:
        cls = "neutral"
    return frac, cls, chrom / max(dirty, 1)


def main():
    ap = argparse.ArgumentParser()
    here = os.path.dirname(os.path.abspath(__file__))
    ap.add_argument("--assets",
                    default=os.path.join(here, "..", "..", "app", "src", "main", "assets", "mock"))
    ap.add_argument("--strict", action="store_true")
    args = ap.parse_args()

    assets = os.path.abspath(args.assets)
    items = sorted(glob.glob(os.path.join(assets, "item-*.png")))
    effects = sorted(glob.glob(os.path.join(assets, "effect-*.png")))
    if not items:
        print(f"FAIL  未找到任何 item-*.png：{assets}")
        return 1 if args.strict else 0

    fails, warns = [], []

    # —— 1. 单品尺寸/比例一致性 ——
    sizes = defaultdict(list)
    for p in items:
        im = Image.open(p)
        sizes[im.size].append(os.path.basename(p))
    print("== 1. 单品尺寸分布 ==")
    for sz, names in sorted(sizes.items()):
        print(f"  {sz[0]}x{sz[1]}  ×{len(names)}")
    if len(sizes) > 1:
        fails.append(f"单品图存在 {len(sizes)} 种规格（应统一）：" +
                     "；".join(f"{k}×{len(v)}张" for k, v in sorted(sizes.items())))
    else:
        print("  OK 全部单品同规格")

    # —— 2/3. 逐图背景与底缘 ——
    print("== 2/3. 背景纯净度 & 底缘残片 ==")
    for p in items:
        name = os.path.basename(p)
        im = Image.open(p).convert("RGB")
        stds, bg = corners_bg(im)
        worst_std = max(stds)
        if worst_std > CORNER_STD_MAX:
            warns.append(f"{name} 四角背景 stddev={worst_std:.1f}>{CORNER_STD_MAX}"
                         "（背景可能带场景/杂物）")
        frac, cls, chrom_frac = bottom_debris(im, bg)
        if frac > BG_DIRTY_FRAC:
            kind = {"chromatic": "疑邻物残片", "neutral": "疑投影/暗部",
                    "garment": "疑本品贴底"}.get(cls, cls)
            line = (f"{name} 底缘异色 {frac:.0%}（{kind}·chrom={chrom_frac:.0%}）")
            if name in KNOWN_DEBRIS:
                # 视觉实锤优先于启发式（黑残片会与本体同色而漏判）
                warns.append(line + " [走查已实锤]")
                print(f"  ! {name} [已实锤残片]")
            elif cls == "chromatic":
                fails.append(line + " → 待视觉复核")
                print(f"  ! {name} [FAIL 疑残片·待复核]")
            elif cls == "garment":
                print(f"  · {name} 底缘 {frac:.0%} = 本品贴底（正常）")
            else:
                warns.append(line)
                print(f"  ? {name} [{kind}]")
    missing = KNOWN_DEBRIS - {os.path.basename(p) for p in items}
    print(f"  已知残片清单（{len(KNOWN_DEBRIS)} 张·视觉实锤）：{sorted(KNOWN_DEBRIS)}"
          + (f"；缺失 {sorted(missing)}" if missing else "；均在，待重出图净化"))

    # —— 4. 效果图 ——
    print("== 4. 效果图 ==")
    esizes = defaultdict(int)
    for p in effects:
        esizes[Image.open(p).size] += 1
    for sz, n in sorted(esizes.items()):
        print(f"  {sz[0]}x{sz[1]}  ×{n}")
    if len(esizes) > 1:
        fails.append("effect-*.png 尺寸不统一")
    print("  提示：主体安全边距（帽顶/鞋底留白 ≥5%）属视觉语义，"
          "由 ui-audit 视觉抽检覆盖（已知 effect-commute 底部 <3%）")

    # —— 汇总 ——
    print("\n== 汇总 ==")
    for f in fails:
        print(f"FAIL  {f}")
    for w in warns:
        print(f"WARN  {w}")
    if not fails and not warns:
        print("全部通过")
    print(f"\n{len(fails)} FAIL / {len(warns)} WARN（目标态：比例统一+残片清零后重出图）")
    if args.strict and fails:
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
