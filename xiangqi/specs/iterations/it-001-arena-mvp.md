# it-001 · AI 象棋竞技场 MVP

> 状态：**提案已确认**（2026-09-23，经 plan 批准）→ 实施中

## 背景与动机

仓库首个 web 应用。用同一套中国象棋规则与同一套走法协议，让两个大模型（各自隔离的 pi agent session）持续对弈，观战并累计胜负——以最直观的方式比较模型棋力与输出稳健性。

## 涉及用户故事

- US-01 选模型开局
- US-02 实时观战与思考流
- US-03 暂停 / 单步 / 手动代走
- US-04 记分板与换先连战
- US-05 导出棋谱

## 验收标准

1. `node server.mjs` 启动，浏览器打开本地端口即见 W1 主界面。
2. 红黑双方可各自从下拉任选 provider/model（含 `models.json` 自定义项）；开局后生成两个隔离 session 的 pi 子进程。
3. 完整跑通一局（GLM 内战冒烟：≥30 手或至终局），期间：
   - 每手落子前端棋盘同步、着法表出中文记谱；
   - 当前方思考流实时可见；
   - 非法走法触发纠错重试且 ≤3 次（冒烟中至少观察到一次重试或以工具证明该路径）。
4. 终局判定与记分板 W/D/L 正确累计；换先连战可开下一局；无孤儿 pi 进程。
5. 暂停/单步/手动代走可用；导出棋谱为文本且不含 key。
6. key 缺失、pi 未装两种故障在 UI 有可读报错。

## 影响范围

- 新增 `xiangqi/`（specs + server.mjs + public/ + vendor/）。
- 根 `DESIGN.md` 个性轴表加一行；根 `README.md` 应用总览加一行；`CHANGELOG.md` 加一行。
- 不改动 wardrobe/eats/island/clips 与 libs/。

## 技术选型（已定，见 ADR）

- pi（earendil-works/pi）`--mode rpc` ×2，隔离 session；退路 `pi -p`。
- 规则：xiangqi.js（BSD-2，vendored 单文件，前后端共用）；棋盘：xiangqiboardjs（MIT）+ jQuery。
- 协议：ICCS + legal_moves 白名单 + JSON 输出 + 重试 ≤3（ADR-001）。
- 传输：零依赖 SSE + POST（ADR-003）。

## 验证记录

> 2026-09-23 实施完成，以下逐条对应验收标准。

**1. 启动**：`node server.mjs` → `http://localhost:8777`，W1 主界面渲染正常（截图 `assets-it-001/w1-arena-midgame.png`）。

**2. 模型任选 + 隔离 session**：`GET /api/providers` 经 pi `get_available_models` 枚举出 18 个模型（source=pi），合并 `~/.pi/agent/models.json` 自定义项（已注册 `arena-local` 假模型 provider 作离线回归）；开局 spawn 两支 `pi --mode rpc --session-dir .sessions/game-*/red|black --no-tools …`，session 文件各含 150 条 user/assistant 消息，证明每手均真实经过 pi（非旁路）。

**3. 完整对局 + 思考流 + 重试**：`node tools/smoke.mjs` 双阶段 **PASS**（不依赖任何真 key）：
- 阶段一：mock-dumb vs mock-smart 跑满 300 手触发 `draw-max` 终局，记分板 W/D/L 正确累计，中文记谱全量生成（含「前炮退1/后炮平5」消歧）；
- 阶段二：mock-bad 首答必非法 → SSE 观测到 **39 次 retry 事件**，纠错后继续对局（≤3 重试路径实证）；
- 对局结束两支 pi 子进程 exit code=0，无孤儿进程（teardown 验证）。

**4. UI 走查（浏览器实测，2 处截图）**：
- `assets-it-001/w1-arena-midgame.png`：中盘暂停态——棋盘/棋子正常、状态条「第 96 回合·手数 191」、思考流含回合分隔与模型输出、着法表与记分板同步；
- `assets-it-001/w3-gameover.png`：终局遮罩「和棋 · 150 回合上限」+ 记分板。
- 走查中修复 4 个真 bug：① `[hidden]` 被 `.overlay{display:grid}` 覆盖（终局遮罩常驻）；② `els` 误用 jQuery 对象挂原生 DOM API（状态条/着法表/记分板/重试徽标全部静默失效）；③ `index.html` 漏引 xiangqiboardjs.css（90 格无 float 竖排 5310px、棋盘不渲染）；④ 暂停后「继续」按钮被 `st!=='playing'` 锁死。
- 手动代走、暂停/单步经页内事件实测可用；SSE 断线重连后 `state` 全量 resync 正常。

**5. 棋谱导出**：`POST /api/game export` 返回含双方模型、Result、中文记谱与 FEN 序列的纯文本（不含 key）。

**6. 故障可读性**：pi 未装 → spawn `error` 事件转为中文报错行；模型无凭据 → 回合报「模型未返回文本：…」并判负，UI error-bar 展示。

**真模型对局（Flash vs Flash，Leo 指定）**：

- **凭据配置**（2026-09-23 Leo 提供两把 key）：GLM key 与 MiMo TokenPlan key 已写入 pi 原生 `~/.pi/agent/auth.json`（600 权限，`{type:"api_key"}` schema），免 env 生效；三家 provider 真调连通全过（`zai`、`zai-coding-cn`、`xiaomi-token-plan-cn` 各发一条补全均正常返回）。服务端启动改为 auth.json 存在即跳过钥匙串（旧钥匙串注入会无限阻塞启动，已修）。
- **第一局**：`zai-coding-cn/glm-5.3-flash`(红) vs `xiaomi-token-plan-cn/mimo-v2.6-flash`(黑)，双方真实轮流调 API，前 15 手正常（屏风马开局套路、思考流含真实棋评），**第 16 手红方连续两次 120s 超时 → `forfeit-timeout` 判负**——ADR-004 超时判负路径被真局端到端实证。研判：疑似撞 GLM 端限流退避（单发补全此前秒回）。
- **第二局**：`TURN_TIMEOUT_MS=240000` 放宽后同对阵重跑，19 手黑方（mimo）两次 240s 超时判负。
- **两局超时的诊断与修复（关键教训）**：解剖双方 session jsonl 发现被判「超时」的一方都留有**正在流式输出的思考内容**——模型不是挂死，是在合法长思考（mimo 单步想了 40+ 分钟），被第一版「总时长超时」硬中断。**改为静默超时**（`TURN_TIMEOUT_MS` = 无任何输出的时长，默认 120s；只要持续吐 token 就不计时），同步修订 ADR-004 与 US-03 AC-3。
- **第三局（静默超时版，验收局）**：同对阵重跑，**60 手 / 30 回合零判负达成 AC「≥30 手」**（第 61 手按暂停语义走完后暂停保留现场）。期间：
  - **5 次非法走法全部自动纠错自愈**（`i9i7`、`a6g6`、`a3a4`、`e3e5`、`e3i2`），重试路径在真模型上实测 5 回；
  - mimo 单步 40+ 分钟长考不被掐断（静默超时生效），思考全程经 SSE 实时可见（12s/42 增量实测）；
  - 真实中局质量：屏风马对中炮、前后消歧记谱（前炮平5/后车进二）、吃子交换与过河兵残局；
  - **上下文隔离实测**：双进程双 session 文件，互相 grep 不到对方 prompt/思考专属串，仅共享服务端拼装的公开棋谱。
- 连通性：GLM key 同时通 `zai`（api.z.ai）与 `zai-coding-cn`（open.bigmodel.cn），MiMo key 通 `xiaomi-token-plan-cn`，三家各一次单发补全秒回。
- 截图：`assets-it-001/w2-real-flash-game.png`（真局实时思考流）、`assets-it-001/w4-final-60plies.png`（61 手终盘暂停态）。

**验收结论**：AC 1-6 全部满足。遗留建议（it-002 候选）：长考绝对上限（如 10 分钟硬顶）防无限思考、回合 thinking 档位可调（`--thinking off` 提速）、长将/重复局面裁定。

**终局补记（2026-09-24 02:00）**：60 手验收暂停后经「继续」恢复，**第 102 手自然终局——黑方（mimo-v2.6-flash）困毙胜红方（glm-5.3-flash），记分板 mimo 1:0 glm**。全程零超时零判负，累计 6 次非法走法全部自愈；ADR-004 的 `stalemate` 路径首次在真局自然触发（此前 mock 覆盖 checkmate/draw-max、真局1/2 覆盖 forfeit-timeout）。收官过程被思考流完整记录：mimo 在最后一步前的推理里明确写出「harmless move preserving the winning net → red is 困毙 → black wins」，随后象5退3 应验；红方光帅被车马炮围猎，仅能 「forced f2f1 now」。对局结束 pi 子进程全部回收（0 残留）。终局截图：`assets-it-001/w5-final-stalemate.png`。
