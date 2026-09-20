# 走查报告 · 演示模式与数据层 SDK 化（eats it-006 / wardrobe it-015）

- **交付物**：`走查报告-演示模式与数据层SDK.pdf`（14 页，A4）
- **走查对象**：两应用演示模式（应用内 Mock 数据源）+ eats 数据层 libs/store 迁移
- **方法**：演示模式下 18 页实机截图 → 两轮视觉模型逐页评审（含放大取证/MD5 查重）→ 交互实测断言链 → 灰盒改版线框（7+1 张，蓝色徽标对应要点）

## 结论速览

- 演示模式功能全部验证通过：开关往返 ×2、写入不落盘（删 1 家→退出→真实 9 家原样）、内置照片渲染
- eats 数据层迁移无恙：单测全绿、老数据无缝读起（磁盘格式不变）
- 走查驱动修复：mock 种子 15 处「名称/颜色与配图矛盾」当场对齐并复验（导出预览标签已一致）
- 剩余问题：P1×12 / P2 若干，按 8 个主题页给出改版线框；落地建议拆 eats it-007、wardrobe it-016/017

## 重出方式

```bash
cd reports/2026-09-20-demo-mode-audit
python3 draw_wireframes.py          # 重绘线框（assets/wf-*.png）
python3 build_report.py report.html # 数据区在 build_report.py 顶部
NODE_PATH=$(npm root -g) node <pdf-skill>/scripts/html2pdf-next.js \
  report.html --output 走查报告-演示模式与数据层SDK.pdf --width 210mm --height 297mm
# 质量门：poster_validate check-html → pdf_qa --no-tables → pdf:visual-judge 全页验收
```

- `assets/`：18 张现状截图 + 8 张改版线框
- `pages/`：PDF 逐页渲染（验收留档）
