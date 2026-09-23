# it-002 · Jev 胜率评估与胜率曲线

> 状态：提案已确认（2026-09-24 Leo 指令原文：「接入 jev 模型来判断每一步红黑双方的胜率，然后加一个胜率曲线图在界面上」，key 随指令提供）→ 实施中

## 背景与动机

it-001 让两个模型对弈后，观战只能靠看棋评「感觉」谁占优。接入 TypeSafe 的 **Jev**（System One 结构化裁决模型：传 state + 类型化问题，直接返回校准概率，无文本生成）对**每手落子后的局面**评估红/黑/和概率，并在界面画出胜率曲线——「谁比较聪明」从叙事变成可读的量化曲线。

## 涉及的用户故事

- 新增 **US-06 每手胜率评估与曲线**：
  - AC-1：每手落子后（含手动代走）异步评估，`概率(红胜)+概率(和)+概率(黑胜)=1`，随 `eval` 事件实时推送，曲线逐点生长；评估失败静默降级（该点留空），绝不阻塞对局。
  - AC-2：侧栏「胜率曲线」面板：红胜率线（红）+ 黑胜率线（黑）+ 50% 基线，落子即更新；刷新页面后历史曲线随 state 全量恢复。
  - AC-3：key 缺失时面板显示「未配置 JEV_API_KEY」且对局不受影响；key 存于 `xiangqi/.env.local`（gitignore，永不入 git/日志）。
  - AC-4：导出棋谱每手附评估值（如有）。
  - AC-5（修订 2026-09-24 Leo 指令）：**评估按局可选**——控制条「评估胜率」勾选框，开局随参数生效、运行中可即时开关（`eval-toggle`），关闭后新落子不评估、面板显示「评估已关闭」。
  - AC-6（修订同上）：**发给 Jev 的 prompt 全英文**——state 字段（game/fen/board/to_move/last_move/recent_history_iccs）、instructions、criteria 均英文；中文记谱与 UI 文案不进 prompt。

## 技术要点（契约已核对 docs.typesafe.ai）

- `POST https://api.typesafe.ai/v1/systemone`，`Authorization: Bearer <key>`，`model: "jev-latest"`
- `state`：{fen, ascii 棋盘, 中文记谱历史, 轮次, 最后一手}；`questions.outcome = {type:"choice", instructions, criteria:{red_win, draw, black_win}}`
- 响应 `answers.outcome.probabilities` → `Move.eval = {red, draw, black, confidence}`
- 评估走**串行队列**（不阻塞回合循环），ply 定位回填；`JEV_DISABLED=1`（假模型冒烟用）可整体关停

## 影响范围

- `xiangqi/server.mjs`（env 加载 + Evaluator 队列 + eval 广播/回填/export）、`public/index.html|app.js|style.css`（面板+SVG 曲线）、`tools/smoke.mjs`（JEV_DISABLED）、`.gitignore`（.env.local）
- specs：01（US-06）、02（W1 加面板）、03（Move.eval / EvalPush）、04（Evaluator 模块）、05（曲线 token）
- `CHANGELOG.md` 一行

## 验证记录

> 2026-09-24 实施完成，逐条对应 AC。

- **契约真调**：`POST /v1/systemone` HTTP 200，返回 `answers.outcome = {type:"choice", choice, confidence, probabilities:{red_win, draw, black_win}}`，与文档一致。**网络坑**：本机 mac curl(LibreSSL) 访问 `api.typesafe.ai` TLS 被重置，**Node(OpenSSL) 正常**——服务端用 `https.request` 恰好绕开（已记入 04-architecture）。
- **AC-1 实测**：新开局后手动推 4 手合法棋（炮二平五/马2进3/马二进三/象3进1），全部异步评估回填——红 14→15→12→26%、和 83→84→85→69%、置信 53~78，`eval` 事件广播正常；评估不阻塞（与落子并发）。非法/错方手动走法被服务端拒绝（`b7c7` 含炮架校验、`a0b0` 错方）。
- **AC-2 实测**：「胜率曲线」面板渲染 SVG 双线（红实黑虚）+50% 基线 +末端 26%/5% 直标 + 图例（红/和/黑/置信/手数），刷新后经 state 全量恢复。截图：`assets-it-002/winrate-curve.png`。
- **AC-3**：key 写入 `xiangqi/.env.local`（600，`git check-ignore` 验证命中）；启动日志与 state 只暴露 `jevEnabled: true` 布尔，不回传 key；`childEnv` 显式剔除 `JEV_API_KEY`（不下注 pi 子进程）。
- **AC-4**：`tools/smoke.mjs` 注入 `JEV_DISABLED=1`，回归双阶段 **PASS**（300 手 draw-max + 33 次重试，零真评估调用）。

**遗留候选**：评估点为异步乱序回填，极端情况曲线中段可能先亮后段（ply 定位已保证不串位）；jev 对复杂中局的校准质量可后续用对局结果做回验。

**修订验证（2026-0024 当日，AC-5/AC-6）**：mock 局三态实测——① `start(evalEnabled:false)` → 手动走子 `lastEval=None`（无评估）；② `eval-toggle on` → 下一手 `红20/和62/黑18` 回填 PASS；③ `toggle off` → `evalEnabled:false` 生效。英文 prompt 已按 AC-6 落地（代码字面量全英文，历史改走 ICCS 序列）。修掉实施期一个引用错误（`startGame` 解构作用域缺 `evalEnabled` 参数，HTTP 报 `body is not defined`）。
