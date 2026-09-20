---
name: ui-audit
description: 对移动 App（Android 模拟器）做全页面 UI 走查与优化评审：安装最新包 → 逐页导航截图 → 视觉模型逐页严格评审（P0/P1/P2 问题清单）→ 交互实测验证 → 自绘灰盒线框给改版方案 → 生成「现状截图 vs 改版线框」对照式 PDF 报告。当用户要预览/走查/review 应用、对页面截图评审、找 UI/UX 问题、给优化方案或线框图、出 UX 评审报告时使用——即使只说"看看这个 App 做得怎么样"也应触发。
---

# UI Audit · 移动 App 全页面走查与优化评审

把一次完整评审拆成 8 个阶段。**先读本文件确定阶段与出口，细节按需读 references/，不要一次性全读。**

```
0 定范围 → 1 装最新包 → 2 逐页采集 → 3 交互实测 → 4 视觉评审
→ 5 综合归类 → 6 线框方案 → 7 报告 → 8 交付
```

**两个出口，开工前确认**（默认问一句，或按用户原话判断）：
- **轻量**：阶段 0–5 + 聊天内交付（截图以 CDN 图片内嵌 + 问题清单 + ASCII 线框）
- **完整**：全 8 阶段，交付 PDF 报告（用户说"报告/正式/给别人看"时选这个）

## 阶段 0 · 确定范围

1. 读应用的 `specs/02-wireframes.md`（或同类）拿到页面清单 W1–Wn 和交互路径——这是导航脚本和"页面覆盖度"的验收基线。
2. 确认演示数据存在（没数据的页面评不了空态以外的东西；需要就先跑 `tools/demo-data` 类脚本）。
3. 列出要采的**过程态**：抽取落定、随机一套、筛选后、弹层展开……每个交互高潮状态单独一张图。

## 阶段 1 · 装最新包

- `adb devices` 确认模拟器在线；不在线先启动 AVD。
- 每个应用 `gradle installDebug`（可后台并行）。**评审必须基于最新代码构建**，装旧包会把已修的问题又报一遍。
- 细节与 adb 环境坑 → 读 `references/adb-capture.md`。

## 阶段 2 · 逐页采集

- 用 `scripts/dump_ui.py` 拿当前界面的可点元素与坐标 → `adb shell input tap` 导航 → `adb exec-out screencap -p > 页面名.png`。
- 命名：`<app>-<序号>-<页面>.png`，过程态用 `-drawn`/`-filtered` 等后缀。**命名即报告资产清单**，一次定好。
- 底部导航坐标注意：键盘弹出会把它顶出屏幕（`keyevent 111` 收键盘）。
- 每页导航后 dump 一次，顺手记录文本树——阶段 3、4 都要用。

## 阶段 3 · 交互实测（评审结论的证据链）

视觉模型说的问题**不算实锤**。对每条关键结论选一种验证：
- **uiautomator 断言**：操作后 dump，确认元素存在/不存在/坐标（如"落定后确认按钮不在可视区"）。
- **源码核对**：grep 对应 Screen/ViewModel，确认逻辑与配色等实现事实（如 marker 颜色 token）。
- 注意：Compose 控件不暴露 `selected` 状态；dump 的属性顺序 `bounds` 常在末尾。
- 实测发现的"反直觉真相"（功能其实在、只是没线索）往往是 P0 的根因——单独记录。

## 阶段 4 · 视觉评审（逐页）

- 本环境 **Read PNG 只返回 CDN URL、看不到画面**——"看图"必须走视觉模型：`Read` 拿 URL → `analyze_image(imageSource=URL, prompt=…)`。
- URL 签名含 `+` 时视觉接口会 400：先试 `%2B` 编码，仍失败就改文件名重 Read 拿新 URL。
- 每页一个 prompt，包含：页面身份（什么 App 哪个页）+ 界面结构描述 + 对标标准（如 Airbnb/Things/Linear）+ 要求 P0/P1/P2 分级 + 每条给具体位置。
- prompt 模板与输出整理法 → 读 `references/vision-review.md`。

## 阶段 5 · 综合归类

- 把逐页问题**合并去重**成：① 跨页面共性问题表（C1…Cn：问题/涉及页/级别/修法）——这是最有行动价值的产出；② 各页问题清单（带严重度标签）。
- 数一遍 P0/P1 总数，写进报告总评。

## 阶段 6 · 线框方案（灰盒 + 变更点徽标）

- 用 `scripts/wireframe_lib.py`（PIL）：画布 720×1600（与 1080×2400 截图同比例 0.45），灰盒风格，**蓝色圆徽标①②③标变更点**——徽标编号与报告侧栏要点一一对应，这是"现状 vs 改版"可读性的关键。
- **符号必须自绘**（sym_check/sym_chev/...）：CJK 字体没有 ✓ ‹ › ▾ ⌖ ✎ 和 emoji 的字形，直接写字符会渲染成叉框方块（实测踩过）。相机/放大镜/定位等图标也画几何形。
- 画完**先自检再嵌入**：拼 contact sheet → analyze_image 查"文字出界/元素重叠/徽标贴边/畸形图形"，修完再进报告。
- 样式规范与自检清单 → 读 `references/wireframes.md`。

## 阶段 7 · 报告（HTML → PDF）

- `scripts/report_template.py`：数据驱动，往顶部 THEMES/COMMONS/TESTS 填内容即可出 21 页式结构（封面/总评/每主题一页对照/拆分建议/附录）。
- 渲染用 pdf 技能的 `html2pdf-next.js`。**铁律：全程 flex 流式布局，禁用 position:absolute**——该转换器会把 absolute 强转 static，封面必然塌叠（实测踩过）。
- 质量门三件套，缺一不可：`poster_validate check-html` → 渲染 → `pdf_qa.py` + 元数据；然后**视觉验收门**：把 PDF 页面渲成 PNG，分两批派发 `pdf:visual-judge` 子代理，问题定点修复后复核。
- 版式参数（图宽 76mm、A4 内容高预算、防溢出）→ 读 `references/report-pdf.md`。

## 阶段 8 · 交付

- 文件清单（PDF/HTML/assets/tools）+ README（含重出方式）。
- 聊天里内嵌预览图：Read PNG 拿 CDN URL，**逐字复制**（签名改一个字符就 403），URL 约 2 小时过期。
- 报告本身不动应用代码；落地建议按用户仓库的迭代流程（如 spec-driven 的 it-XXX）单独拆分。

## 踩坑速查（全都是实测）

| 坑 | 解法 |
|---|---|
| Read PNG 看不到画面 | analyze_image + CDN URL 才是"看图" |
| 视觉接口对含 `+` 的 URL 报 400 | `%2B` 编码或改名重 Read |
| 聊天内嵌图裂开 | CDN URL 逐字复制，绝不凭记忆重写 |
| 键盘盖住底部导航、点击失效 | `keyevent 111` 收键盘再导航 |
| uiautomator 里 Compose chips 无 selected | 用行为验证（点击前后 dump 对比），别信属性 |
| 线框出现叉框方块 | 字体缺字形，改自绘 sym_* |
| 封面/分节页排版塌叠 | html2pdf-next.js 把 absolute 转 static，用 flex + margin-top:auto |
| 附录页页脚溢出到新页 | A4 内容超 297mm 必溢出，压表格行距/图高 |
| 评审报了已修的问题 | 装最新构建再评；拿不准就核对 git log |
