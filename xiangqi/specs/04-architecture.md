# 04 · 架构（Architecture）

> 状态：it-001 起草（2026-09-23）

## 分层

```
┌─ 浏览器（零构建单页） ────────────────────────────────┐
│ public/index.html + app.js + style.css               │
│ xiangqiboardjs（棋盘渲染） + xiangqi.js（规则/记谱）  │
│   ↑ SSE /api/events        ↓ POST /api/game         │
├─ Node 服务（server.mjs，零 npm 依赖）────────────────┤
│ http 静态服务 · GameOrchestrator 回合循环             │
│   · Providers 目录（读 ~/.pi/agent/models.json）      │
│   · xiangqi.js（Node 侧规则权威：白名单/落子/终局）    │
├─ pi 子进程 ×2（--mode rpc，隔离 session）────────────┤
│ 每方一个：--provider X --model Y --session-dir …     │
│   stdin JSONL 命令 / stdout JSONL 事件+响应          │
└─ 模型厂商 API（BYOK，key 在 pi 侧）──────────────────┘
```

## 模块职责

| 模块 | 文件 | 职责 |
|---|---|---|
| HTTP 入口 | `server.mjs` | 静态文件、`GET /api/providers`、`SSE /api/events`、`POST /api/game`（start/pause/step/resume/human-move/export） |
| GameOrchestrator | `server.mjs` 内 | 回合循环、prompt 拼装、重试/超时、终局判定、SSE 广播、记分板 |
| PiClient | `server.mjs` 内 | spawn/kill `pi --mode rpc`、JSONL 分帧（按 `\n`，不用 readline）、请求-响应配对、事件转发 |
| Providers | `server.mjs` 内 | 合并 pi 内置 presets 与 `~/.pi/agent/models.json`，供 UI 下拉 |
| 规则引擎 | `vendor/xiangqi.js` | 前端展示 + 后端校验**同一文件**，避免两套规则漂移 |
| 棋盘 UI | `vendor/xiangqiboardjs` | 纯渲染，不懂规则；点击代走由 `app.js` 调 xiangqi.js 校验 |

## 依赖规则

- 前端不 import 任何 key/厂商信息；providers 列表只暴露 id/name。
- xiangqi.js 是唯一规则权威；orchestrator 与前端都只能通过它取合法走法。
- PiClient 是唯一 spawn 点；所有子进程生命周期由 orchestrator 托管（pause/over/exit 必杀）。

## 回合时序（一个回合）

```
orchestrator → xiangqi.legalMoves() → prompt(FEN+ASCII+白名单+上一手非法纠错?)
  → PiClient.rpc(side, prompt, timeout=60s)
  ← 提取 {"move":"h2e2"} → 白名单校验
     ├─ 非法 && retry<3 → 带错误信息再次 prompt（SSE 发 retry 徽标）
     ├─ 非法 && retry=3 → 终局 forfeit-illegal
     └─ 合法 → apply → zh 记谱 → 终局判定? → SSE board/move/thinking → 下一方
```

## 关键外部接口

- pi RPC：`stdin` 写 `{"id","type":"prompt",...}`，`stdout` 读 `{"id","type":"response"}` 与事件流至 `agent_settled`（以官方 `rpc.md` 为准，实现前 spike 验证）。
- 退路：RPC 不稳则切 `pi -p` 每回合一次性子进程（`--session-id` 续会话），只换 PiClient 内部实现，编排层接口不变。
