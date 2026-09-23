# CHANGELOG

> AGENTS.md 迭代流程第⑤步要求的变更流水；本文件于 it-001 建立（此前仓库未落地该约定）。

## 2026-09-25

- **docs(xiangqi)**: it-003 过夜双局结果——Flash 档 mimo-v2.6-flash 将死胜 glm-5.3-flash（76手/105分），Pro 档 glm-5.3 将死胜 mimo-v2.6-pro（53手/109分），四模型 1:1；报告+双局快照/棋谱入库 `reports/2026-09-24-xiangqi-showdown/`，it-003 验证记录回填（含无限等待 Promise.race 假判负坑）。

## 2026-09-24

- **feat(xiangqi)**: it-002 Jev 胜率评估与曲线——每手落子后异步问 TypeSafe Jev（Choice: 红胜/和/黑胜概率）回填 `Move.eval` 并广播，侧栏纯 SVG「胜率曲线」（红实黑虚双线+50%基线+置信图例），导出棋谱附评估值；key 入 `.env.local`（gitignore），`JEV_DISABLED` 关停冒烟；specs 01/02/03/04/05 同步，smoke 回归 PASS。网络坑：mac LibreSSL 访问 api.typesafe.ai 被掐、Node OpenSSL 正常。**修订**：评估按局可选（勾选框+`eval-toggle` 运行中切换，三态实测 PASS）、发给 Jev 的 prompt 全英文（state/历史走 ICCS）。

- **feat(xiangqi)**: it-001 真模型验证收口——GLM+MiMo 两把 key 入 pi `auth.json`（免 env、跳过会阻塞的钥匙串注入）；超时语义由总时长改**静默超时**（真局实证长思考被误杀后修复，ADR-004/US-03 同步）；Flash 对决第三局 60 手零判负达 AC 并最终**第 102 手困毙自然终局（黑方 mimo 胜，记分板 1:0）**、6 次非法重试自愈、上下文隔离实测；思考流同类增量合并成段；前端默认对阵 `glm-5.3-flash vs mimo-v2.6-flash`。

## 2026-09-23

- **feat(xiangqi)**: it-001 AI 象棋竞技场 MVP——仓库首个 web 应用：`server.mjs`（零依赖 http+SSE+POST）spawn 两个 `pi --mode rpc` 隔离 session 子进程，FEN+合法走法白名单+ICCS 协议驱动对弈（非法重试 ≤3、超时重试、将死/困毙/60 回合无吃子/150 回合上限裁定），前端 xiangqiboardjs+xiangqi.js 观战 UI（思考流/中文记谱/记分板/手动代走/棋谱导出），specs 全套 + 假模型全链路冒烟（`tools/smoke.mjs` 双阶段 PASS）。
