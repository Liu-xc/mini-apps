# UX 评审报告 · 吃啥 × 衣橱（2026-09-20）

对 `eats` / `wardrobe` 两款应用 15 个页面的实机截图走查 + 视觉模型逐页评审 + 交互实测，
产出按严重度分级的问题清单与逐页改版线框。

## 文件

| 文件 | 说明 |
|---|---|
| `UX评审报告-吃啥×衣橱-2026-09-20.pdf` | 最终报告（21 页 A4，矢量文本） |
| `report.html` | 报告 HTML 源（改数据后可重出） |
| `preview.png` | 三页预览拼图（封面 / 主题页样例） |
| `assets/eats-*.png`、`assets/wardrobe-*.png` | 模拟器实机截图（原图 + 证据图） |
| `assets/wf-*.png` | 改版线框图 15 张（灰盒 + 蓝色变更点徽标①②③） |
| `tools/draw_wireframes.py` | 线框图生成脚本（PIL + Hiragino Sans GB，符号全部自绘避免缺字） |
| `tools/build_report.py` | 报告 HTML 生成脚本（数据驱动，问题/要点在 `themes` 列表里改） |

## 重出方式

```bash
# 1) 重画线框（需要 pillow）
python3 tools/draw_wireframes.py
# 2) 重出 HTML
python3 tools/build_report.py
# 3) HTML → PDF（html2pdf-next.js；注意：该转换器会把 position:absolute 转 static，
#    报告已全程使用 flex 流式布局规避）
node <pdf-skill>/scripts/html2pdf-next.js report.html --output 报告.pdf --width 210mm --height 297mm
```

## 结论速览

- 13 项 P0 / 14 项 P1 / 若干 P2；集中在四类共性问题：
  C1 滑动无可发现线索、C2 保存只在顶栏、C3 键盘无避让、C4 核心动作不吸底
- 建议拆 `eats/it-004`（交互补课）与 `wardrobe/it-011`（可发现性与叙事）两个迭代落地，见报告 P20
