# 暗室 darkroom · UI 审查与功能自测交付

> 2026-09-27 · ui-audit 全流程（走查 → 实测 → 线框 → PDF 报告）

## 交付文件

| 文件 | 说明 |
|---|---|
| **显影darkroom-UI审查报告.pdf** | 9 页 A4：封面 / 总评（方法+共性表）/ 分节页 / 主题 D1–D4 现状 vs 线框对照 / 落地拆分 / 附录实测+证据 |
| `report.html` | 报告源（数据驱动，改数据可重出） |
| `assets/` | 11 张实机截图 + 4 张改版线框 + 1 张证据裁剪 |
| `tools/make_wires.py` | 线框绘制（wireframe_lib 灰盒 + 蓝色徽标） |
| `tools/make_report.py` | 报告生成（数据区在文件顶部） |
| `tools/render_pdf.js` | Playwright/Chrome 打印管线（替代本机缺失的 html2pdf-next.js） |
| `qa/page-01..09.png` | PDF 逐页 100dpi 渲染（视觉验收留档） |

## 结论速览

- **P0×1（已修复）**：定影落定必崩——manifest 缺 `VIBRATE`，`Haptics.kt:28` 抛 SecurityException；
  补 normal 权限后全流程回归通过（改动在 `darkroom/app/src/main/AndroidManifest.xml`，已回填 it-001 验证记录）。
- **P1×5**：速度档只改标签（`DarkroomViewModel.kt:72`）· 页头无 statusBars insets（128px 触摸死区）·
  显影台卡面签名区溢出（1028 vs 1158px）· 成片页标题×日期重叠（域 60%>58%）· 首屏 CTA 裁切+进度条屏外。
- **P2×5**、**通过 11 项交互实测**（药水条 seek、甩一甩注入 boost、双导出 MediaStore 落盘、三入口、分享面板、设置持久化）。

## 重出报告

```bash
cd reports/darkroom-ui-audit
python3 tools/make_wires.py assets        # 1) 重画线框
python3 tools/make_report.py report.html  # 2) 生成 HTML（改 tools/make_report.py 顶部数据区）
NODE_PATH=$(npm root -g) node tools/render_pdf.js report.html out.pdf  # 3) 渲染
python3 -c "import pymupdf; d=pymupdf.open('out.pdf'); print(d.page_count)"  # 期望 9
```

视觉验收：`qa/` 下逐页 PNG 派发子代理按「溢出 / 缺字 / 徽标压字 / 图片未载入 / 页脚」清单判定。

## 环境备忘

- 评审基于 `./gradlew installDebug` 当日源码（含 P0 补丁）。
- 模拟器崩溃后曾出现合成器卡死（screencap 恒为旧帧），重启 AVD 恢复；采集脚本 `tools/` 内命令可复用。
- 落地建议按仓库 spec-driven 流程拆 it-002/it-003（见报告「落地拆分建议」页）。
