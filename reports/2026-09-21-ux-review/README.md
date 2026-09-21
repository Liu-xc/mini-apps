# UX 评审报告 · 吃啥 × 衣橱（2026-09-21）

全页面截图走查 + 视觉逐页评审 + 交互实测（uiautomator 断言 / 源码核对），27 张实拍，
P0×0 / P1×2 / P2×7，附 2 张灰盒改版线框（蓝色徽标对应要点）。

## 文件

- **UX评审报告-2026-09-21.pdf** — 最终报告（8 页，pdf_qa PASS + visual-judge 8/8 页通过）
- report.html / build_report.py — HTML 源与数据区（改数据后重跑 `python3 build_report.py` 即可重出）
- assets/ — 27 张实机截图 + 2 张线框 + uiautomator 文本树（*.xml 为评审当时的界面证据）

## 关键发现（详见 PDF）

- **E1·P1** eats 干啥页抽中落定后卡组区整片空白，结果块孤悬屏底（assets/e-02-W1-抽中落定.png）
- **W1·P1** wardrobe 衣橱卡片单击直达「编辑」、无进详情路径（源码 `combinedClickable(onClick=onEdit)` 实锤）
- P2×7：FAB 遮挡列表末行 / 地图噪条（待真机复核）/ 卡内留白 / chips 组合态提示 / W6 预览窄条（已知取舍）/ W9 空指标 / 种子命名不符
- 复验通过：转正表单置灰（it-023）、拔草撤销闭环（it-008）、长图全链路

## 重出方式

```bash
python3 build_report.py
NODE_PATH=$(npm root -g) node <pdf-skill>/scripts/html2pdf-next.js \
  report.html --output "UX评审报告-2026-09-21.pdf" --width 210mm --height 297mm
```
