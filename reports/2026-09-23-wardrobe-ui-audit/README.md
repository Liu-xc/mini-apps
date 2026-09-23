# 衣橱 WARDROBE · UI 审查报告（2026-09-23）

对 wardrobe 全部 10 个页面（W1–W10 + 数据包小节）的实机截图走查、交互实测与改版线框。

## 交付物

| 文件 | 说明 |
|---|---|
| `衣橱UI审查报告-2026-09-23.pdf` | 最终报告（9 页：封面/总评+共性问题 C1–C10/主题对照页×3/落地拆分/附录实测与证据） |
| `衣橱UI审查落地验证报告-2026-09-23.pdf` | 落地验证报告（6 页：封面/C1–C10 复验表/W1·W8 现状→落地对照/ W7·W10·W9 三页复验/口径勘定·emoji 补遗·备案；it-029/030/031 实施后复验） |
| `report.html` | 审查报告源文件（数据驱动，可改后重出） |
| `report-impl.html` | 落地验证报告源文件（手写同套样式，改后按下方命令重出） |
| `report_data.py` | 报告数据区（评审结论单独存放，改这里再重拼） |
| `gen_report.py` | 生成器（report_template.py + 数据区拼接而成） |
| `draw_wireframes.py` | 线框绘制脚本（依赖 ui-audit skill 的 wireframe_lib） |
| `assets/` | 29 张实机截图（`wardrobe-NN-页面.png`）+ 3 张改版线框 + contact sheet |
| `v31-*.png` | 落地后复验截图 ×6（W1/W8/W7 详情/W10 表单/W9 空态/评论标题） |
| `pdfpages/` | 审查报告逐页 100dpi PNG（视觉验收用） |
| `pdfpages_v31/` | 落地验证报告逐页 120dpi PNG（视觉验收用） |

## 核心结论

- **P0×3**：①混入心愿关闭崩溃（`SlotGrid.kt:114`，logcat 实录）；②W8 卡序翻页器压住拼贴帽行；③🌟心愿入口 icon-only emoji 触 §5.2 红线
- **P1×12**：底部导航/弹层偏紫（M3 surfaceContainer 未定义，跌回 Material 默认 #F3EDF7 族）、emoji 功能图标系统性、卡片实体名未衬线、评论删除无确认无撤销（§5.7）、种草表单吸底失效、W7 打卡按钮层级拉平、序号胶囊伪按钮点击穿透等
- **P2×15**：长图预览可读性（it-017 已知限制）、空槽对比度、snackbar 遮 FAB 等

## 复现方式

```bash
# 数据区改动后重出审查报告：
python3 gen_report.py report.html
NODE_PATH=$(npm root -g) node <pdf-skill>/scripts/html2pdf-next.js \
  report.html --output 衣橱UI审查报告-2026-09-23.pdf --width 210mm --height 297mm

# 落地验证报告重出（改 report-impl.html 后）：
NODE_PATH=$(npm root -g) node <pdf-skill>/scripts/html2pdf-next.js \
  report-impl.html --output 衣橱UI审查落地验证报告-2026-09-23.pdf --width 210mm --height 297mm

# 元数据（pymupdf）→ pdf_qa → 视觉验收门（pdf:visual-judge）
```

评审基线：`DESIGN.md` §2–§5（v1.0）+ `wardrobe/specs/02-wireframes.md`（W1–W10）。
构建：git `607d264` 最新代码 `installDebug` + `tools/demo-data.sh`（17 件/5 套）+ UI 真实创建心愿 1 条。
