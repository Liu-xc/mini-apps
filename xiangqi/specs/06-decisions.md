# 06 · 架构决策记录（ADR）

## ADR-001 模型走法协议：ICCS + 合法走法白名单

- **状态**： accepted，2026-09-23
- **背景**：模型输出格式不可靠；中文记谱有前后/左右消歧易错，(x,y) 坐标无业界约定。
- **决策**：内部与模型一律 **ICCS 4 字符**（如 `h2e2`）；prompt 附 FEN + ASCII 棋盘 + `legal_moves` 白名单；要求输出 `{"move":"h2e2"}`，正则提取；非法注入纠错提示重试 ≤3，仍非法判负。中文记谱仅作展示/导出（xiangqi.js 转换）。
- **依据**：MINE-USTC/Xiangqi-R1、Laffinty/llm-xiangqi 同实践；防格式 reward hacking。
- **后果**：白名单可被模型「抄近路」——因每手都是服务器校验后落子，抄也只能抄合法着，可接受。

## ADR-002 双 agent 驱动 = pi `--mode rpc` 双子进程、隔离 session

- **状态**： accepted（退路见下），2026-09-23
- **背景**：用户指定用本地 pi agent、两个隔离 session、web 调本地进程。
- **决策**：每方 spawn 一个 `pi --mode rpc --provider X --model Y --session-dir <game>/<side>`；stdin/stdout JSONL 配对请求-响应与流事件；进程由 GameOrchestrator 托管，终局/重开必杀。
- **退路**：RPC 分帧或协议不稳 → 切 `pi -p` 每回合一次性子进程（`--session-id` 续会话），只改 PiClient 内部。
- **依据**：pi 官方 docs/rpc.md、sdk.md；隔离 session = 隔离子进程最直接。

## ADR-003 零构建单页 + SSE/POST，不用 WebSocket、不引入 npm 依赖

- **状态**： accepted，2026-09-23
- **决策**：`server.mjs` 仅用 Node 内置 `http`；服务→前端事件走 SSE，前端→服务命令走 `POST /api/game`；前端 script 标签 + vendored 组件。
- **理由**：仓库首例 web 应用，最小基建；对局事件天然单向推送，SSE 足够且自动重连。
- **后果**：若未来要高频双向（如观战聊天）再评估 ws，记入新 ADR。

## ADR-004 终局规则（v1 简化裁定）

- **状态**： accepted，2026-09-23
- **决策**：
  - 将死 **或 困毙**（无合法着）→ 判负（中国象棋惯例：被困毙者负）；
  - 连续 **60 回合无吃子** → 和棋（`draw-60`）；
  - **150 回合**上限 → 和棋（`draw-max`）；
  - 3 次非法走法 / **静默超时**（默认 120s 无任何输出，`TURN_TIMEOUT_MS` 可调；流式输出中不计时——真局长思考实证后由总时长超时改为静默超时）重试 1 次仍超 → 判负；
  - **长将、长捉、重复局面不判**（v1 从简，避免实现中国象棋复杂裁定）。
- **后果**：极端对局可能以和棋掩盖违规长将；记为 **it-002 候选**：接重复检测与长将裁定。

## ADR-005 凭据边界（BYOK 红线落地）

- **状态**： accepted，2026-09-23
- **决策**：key 只存在于 pi 自身配置（`~/.pi/agent/auth.json|models.json` 的 `$ENV` 引用）与 macOS 钥匙串；server 启动时可从 island 钥匙串读 GLM key 注入**子进程 env**（不出现在 HTTP 响应、日志只出 `sk-***last4`）；数据包/导出物永不含 key。
- **依据**：libs/agent 三红线；AGENTS.md。
