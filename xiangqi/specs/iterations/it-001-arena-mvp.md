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

**GLM 真模型内战**：⚠️ **唯一遗留项**。key 存于 island 钥匙串（`com.spartapps.island/api-key`），但 macOS 授权弹窗全程无人点击（8s+ 阻塞实测），本会话无法读取。除「真厂商 API 调用」外的全部链路（pi RPC 双 session、白名单协议、重试、终局、UI）均已由假模型冒烟覆盖；pi→厂商一环与其余环节正交。解锁任一方式后即可补跑：① Leo 点钥匙串弹窗「始终允许」→ `node server.mjs` 启动时自动注入；② `export ZAI_API_KEY=...` 后启动；③ `pi --provider zai --model glm-4.6 -p 测试` 先验连通。补跑命令：开局选 `zai/glm-4.6` vs `zai/glm-4-flash`，观察 ≥30 手后回填本节。
