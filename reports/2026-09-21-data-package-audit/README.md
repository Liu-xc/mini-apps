# 数据包功能 UI 走查报告 · 2026-09-21

it-024（wardrobe）/ it-012（eats）数据包导入导出功能的专项 UI 走查。

## 交付物

- **数据包功能UI走查报告-衣橱×吃啥-2026-09-21.pdf** — 最终报告（13 页：封面/总评/7 主题页/落地拆分/附录；pdf_qa PASS + visual-judge 13/13 页通过，其中页 6/7 布局问题修复后复核通过）
- report.html / tools/build_report.py — HTML 源与数据区
- wireframes.py — 两张改版线框（wf-import-replace / wf-replace-confirm，基于 ui-audit wireframe_lib）
- assets/ — 16 张实机截图（模拟器 emulator-5554，debug 构建含 it-024/it-012）+ 2 张线框

## 结论速览

- **P0×2**：替换模式危险态表达不足（单选与合并同权 + 红色摘要动线割裂）、替换明细删除侧缺图片数
- **P1×3**：先导出备份纯文字不可执行、差异/明细行符号无色彩分层（跨应用）、确认按钮不随模式变化
- **P2×7**；两条视觉模型的 P0 初判（✗ 符号豆腐块、导出无反馈）经像素复核与 dump 断言**撤销**
- 交互实测 9 项全 PASS（合并/替换/跨 app 拒绝/坏包/备份恢复/validator 回环等）
- 修复建议拆为 **it-025 数据包交互加固**（O1–O5，见报告落地拆分页），待 Leo 确认

## 重出方式

```bash
python3 wireframes.py        # 可选：重画线框
python3 tools/build_report.py report.html
node <pdf-skill>/scripts/html2pdf-next.js report.html \
  --output "数据包功能UI走查报告-衣橱×吃啥-2026-09-21.pdf" --width 210mm --height 297mm
# pdf_qa 需要 pymupdf：pip3 install --user --break-system-packages -i https://mirrors.aliyun.com/pypi/simple/ pymupdf
python3 <pdf-skill>/scripts/pdf_qa.py "数据包功能UI走查报告-衣橱×吃啥-2026-09-21.pdf"
```
