# CHANGELOG

> AGENTS.md 迭代流程第⑤步要求的变更流水；本文件于 it-001 建立（此前仓库未落地该约定）。

## 2026-09-25

- **fix(wardrobe)**: it-042 第五轮走查修复 C1–C8（P1×2/P2×6，C5 挂账）——W2「使用中」补 8dp 分读、W8 卡组 hero 成品图改衬纸 Fit 修裁头（05「成品图一律 Crop」条款作废）、OutfitThumb 日期并入 yyyy/MM/dd、W1 槽位长按 toast 读全名、W9 打卡 0 隐藏闲置冗余卡、W10 域名字距归零、W1/W3 滚动底缘 28dp 渐隐（共享 `fadingBottomEdge`）；specs 01/02/05 同步，编译+单测绿，模拟器复验回填验证记录。来源=2026-09-25 r5 走查（P0×0/P1×2/P2×6）。

- **docs(xiangqi)**: it-003 过夜双局结果——Flash 档 mimo-v2.6-flash 将死胜 glm-5.3-flash（76手/105分），Pro 档 glm-5.3 将死胜 mimo-v2.6-pro（53手/109分），四模型 1:1；报告+双局快照/棋谱入库 `reports/2026-09-24-xiangqi-showdown/`，it-003 验证记录回填（含无限等待 Promise.race 假判负坑）。

## 2026-09-24

- **feat(xiangqi)**: it-002 Jev 胜率评估与曲线——每手落子后异步问 TypeSafe Jev（Choice: 红胜/和/黑胜概率）回填 `Move.eval` 并广播，侧栏纯 SVG「胜率曲线」（红实黑虚双线+50%基线+置信图例），导出棋谱附评估值；key 入 `.env.local`（gitignore），`JEV_DISABLED` 关停冒烟；specs 01/02/03/04/05 同步，smoke 回归 PASS。网络坑：mac LibreSSL 访问 api.typesafe.ai 被掐、Node OpenSSL 正常。**修订**：评估按局可选（勾选框+`eval-toggle` 运行中切换，三态实测 PASS）、发给 Jev 的 prompt 全英文（state/历史走 ICCS）。

- **feat(xiangqi)**: it-001 真模型验证收口——GLM+MiMo 两把 key 入 pi `auth.json`（免 env、跳过会阻塞的钥匙串注入）；超时语义由总时长改**静默超时**（真局实证长思考被误杀后修复，ADR-004/US-03 同步）；Flash 对决第三局 60 手零判负达 AC 并最终**第 102 手困毙自然终局（黑方 mimo 胜，记分板 1:0）**、6 次非法重试自愈、上下文隔离实测；思考流同类增量合并成段；前端默认对阵 `glm-5.3-flash vs mimo-v2.6-flash`。

## 2026-09-23

- **feat(xiangqi)**: it-001 AI 象棋竞技场 MVP——仓库首个 web 应用：`server.mjs`（零依赖 http+SSE+POST）spawn 两个 `pi --mode rpc` 隔离 session 子进程，FEN+合法走法白名单+ICCS 协议驱动对弈（非法重试 ≤3、超时重试、将死/困毙/60 回合无吃子/150 回合上限裁定），前端 xiangqiboardjs+xiangqi.js 观战 UI（思考流/中文记谱/记分板/手动代走/棋谱导出），specs 全套 + 假模型全链路冒烟（`tools/smoke.mjs` 双阶段 PASS）。
