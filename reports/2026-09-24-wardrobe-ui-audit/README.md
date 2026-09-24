# 衣橱 UI 走查报告 · 2026-09-24

针对 **it-032 演示语料刷新**（36 单品 PNG + 3 效果图 + `wardrobe.json` 随 APK 编译）后的
wardrobe DEBUG 演示模式，做的一轮全页面 UI 走查。

## 结论速览

| 指标 | 值 |
|---|---|
| 编译状态 | ✅ `app-debug.apk` 11:02 构建，无源码比它新 |
| 走查页面/状态 | 14（W1–W10 + 过程态） |
| 素材抽查 | 3 张（item-01 / item-13 / effect-commute，另 item-25、effect-date 由评审覆盖） |
| 问题总数 | **89**（P0×0 / P1×19 / P2×70） |
| 交互实测 | 14 项全 PASS；上轮 C1 崩溃场景 6 轮 0 FATAL |
| 上轮复核 | 9-23 C1–C10 全部已落地复验通过 |

**最重要的新发现**：素材层两批出图比例混用（item-01~24 = 362×362、item-25~36 = 410×319），
是 W1/W3/W12 卡片图片适配不统一、留白/出血混杂的**根因**——已作为 it-035（素材管线统一）拆出。

共性问题 C1–C12 与落地拆分 it-033~036 详见 `衣橱UI走查报告-2026-09-24.pdf`。

## 交付物

```
衣橱UI走查报告-2026-09-24.pdf   ← 主交付（13 页，含元数据）
report.html                     ← PDF 源（数据驱动可重出）
gen_report.py                   ← 报告数据区（改数据重出：python3 gen_report.py report.html）
draw_wireframes.py              ← 7 张灰盒线框绘制脚本
wireframes/                     ← wf-w1/w3/detail/w6/w8/w9/w10.png + contact-sheet.png
shots/                          ← 实机截图 21 张（1080×2400）
review/                         ← 逐页评审原始 JSON（part1/part2）
assets/                         ← 报告内嵌资产（截图+线管线框）
pdfpages/                       ← PDF 逐页 PNG（视觉验收门留档）
```

## 重出方式

```bash
cd reports/2026-09-24-wardrobe-ui-audit
python3 draw_wireframes.py        # 改线框后
python3 gen_report.py report.html
python3 <pdf-skill>/scripts/poster_validate.py check-html report.html
NODE_PATH=$(npm root -g) node <pdf-skill>/scripts/html2pdf-next.js \
  report.html --output 衣橱UI走查报告-2026-09-24.pdf --width 210mm --height 297mm
python3 <pdf-skill>/scripts/pdf_qa.py --no-tables 衣橱UI走查报告-2026-09-24.pdf
```

## 走查口径

- 演示模式开关：`demo_mode` prefs 直写（等价 W3 标题连点 5 次入口）。
- 触控目标以 `uiautomator dump` bounds ÷ 420dp 密度换算，不靠目测。
- 评审图存在 Read 缓存错位：所有评审先按 dump 标题特征自校验，错图重读；
  程序化像素探测（PIL）作为文件内容的最终事实源。
- 愿望卡样式结论以源码核对定案（`SlotGrid.kt:132-192`，it-019/030）。

## 落地

按 AGENTS.md 迭代流程：报告仅给建议，拆分为 it-033~036 提案待确认后动工
（本报告不改应用代码）。
