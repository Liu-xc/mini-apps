# 衣橱 wardrobe · AI 新三面审美走查报告（2026-09-25）

针对 it-040（W5 补抠状态条）与 it-041（W11 设置页 / W12 对话页）刚落地的三个新界面：
10 状态实机截图走查 + 13 项交互实测 + 视觉模型逐页评审 + 4 组改版线框 + PDF 报告。

## 结论速览

**P0 × 0 · P1 × 7 · P2 × 14**（终版口径见 `s5-findings.md`）
七条 P1：C1 关键指引误用弱文本 / C2 W12 会话顶部锚定 / C3 技术词汇外泄（raw 工具名、ADR-024）/
C4 错误语言两套且因果断裂 / C5 主 CTA 幽灵态 / C6 自检结果不持久 / C7 候选态容器不可见。

> 初稿三条结论经实测**撤销/降级**：热区实测全 48dp（撤销「触控不足」）、inkFaint 3.92 达仓库 ≥3:1
> （改定级为语义误用）、清除 Key 有二次确认（降级色彩语义）。

## 文件清单

| 文件 | 说明 |
|---|---|
| `衣橱新三面审美走查报告-2026-09-25.pdf` | **终版报告（9 页）**：封面/总评+C1–C7/分节/4 主题对照页/落地拆分 it-043·044/附录实测+证据 |
| `report.html` + `make_report.py` | 报告源（数据驱动，改数据重出） |
| `assets/wardrobe-01..10-*.png` | 实机截图 10 张（1080×2400） |
| `assets/wf-t*-current/revised.png` | 4 组改版线框（现状+改版 8 张，720×1600，蓝徽标①②③=报告右栏要点） |
| `evidence/` | 6 份 uiautomator dump XML + `s3-assertions.md`（13 条实测断言与源码事实） |
| `s5-findings.md` | 终版问题归类（C1–C12 与各页清单） |
| `qa-pages/` | PDF 逐页渲染（视觉验收门输入，9/9 PASS） |

## 质量门记录

1. `poster_validate check-html`：0 error / 0 warning
2. `html2pdf-next.js` 渲染 → `pdf_qa --no-tables`：**11 项通过 / 0 警告**（首轮 4 警告已修：
   补 author 元数据、NBSP 修行首 `·`、共性表裁至 7 行 P1 修分页溢出、附录压行修页脚顶出）
3. 视觉验收门：`pdf:visual-judge` 分两批 **9/9 PASS**（首轮抓出表格溢出打断分页、附录孤页，已修）

## 重出方式

```bash
cd reports/2026-09-25-wardrobe-newui-audit
python3 make_report.py report.html
NODE_PATH=$(npm root -g) node <pdf-skill>/scripts/html2pdf-next.js report.html \
  --output 衣橱新三面审美走查报告-2026-09-25.pdf --width 210mm --height 297mm
# 再按需 set_metadata + pdf_qa
```

## 备注

- 走查基于最新构建 `9b63b48`（it-041 阶段 B 合入后）；截图采集时演示/正常双模式，真实数据零污染
  （补抠候选一律「还原」不落盘；演示模式写操作重启即弃）。
- 评审/质检子代理的「Read 串图」环境问题全程用像素签名归因 + uiautomator dump 交叉验证兜底。
- 报告不改应用代码；落地建议按仓库 spec-driven 流程拆 **it-043 / it-044**（见报告落地拆分页）。
