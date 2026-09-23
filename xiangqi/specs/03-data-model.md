# 03 · 数据模型（Data Model）

> 状态：it-001 起草（2026-09-23）。无持久化数据库——对局数据驻内存，棋谱按需导出为文本。

## 实体

### Game（对局）

| 字段 | 类型 | 说明 |
|---|---|---|
| id | string | 进程内 UUID |
| red / black | Side | 见下 |
| fen | string | 当前局面（象棋 FEN，xiangqi.js） |
| moves | Move[] | 着法序列 |
| status | enum | `loading` / `playing` / `paused` / `over` |
| result | null \| `{winner: 'red'\|'black'\|null, reason: EndReason}` | 终局才非空 |
| ply | int | 当前半回合数 |
| noCaptureCount | int | 连续无吃子半回合数（ADR-004） |

### Side（一方）

| 字段 | 类型 | 说明 |
|---|---|---|
| color | `'red'\|'black'` | |
| provider | string | pi provider id，如 `zai`、`my-glm` |
| model | string | 模型 id，如 `glm-4.6` |
| sessionId | string | pi 隔离 session 标识 |
| retry | int | 本回合非法走法重试次数（≤3） |

### Move（着法）

| 字段 | 类型 | 说明 |
|---|---|---|
| iccs | string | `h2e2`，唯一权威格式（ADR-001） |
| zh | string | 中文记谱 `炮二平五`（xiangqi.js 转换，展示/导出用） |
| fenAfter | string | 走后局面，供导出回放 |
| by | `'model'\|'human'` | 手动代走标记 |
| retried | int | 到达该着法前的重试次数 |
| eval | null \| `{red, draw, black, confidence}`（0~1） | it-002：jev 对**走后局面**的结局概率，异步回填 |

### EndReason 枚举

`checkmate`（将死） / `stalemate`（困毙） / `forfeit-illegal`（3 次非法判负） / `forfeit-timeout`（超时判负） / `draw-60`（60 回合无吃子） / `draw-max`（150 回合上限）

### Scoreboard（跨局记分）

`{ modelKey: string → {w: int, d: int, l: int} }`，`modelKey = provider/model`；换先连战时随 Game 累计，进程退出即失（v1 不落盘）。

## 存储格式（导出棋谱，US-05）

```
# AI 象棋竞技场棋谱
Red:  zai/glm-4.6    Black: zai/glm-4-flash
Result: 1-0 (checkmate)
1. 炮二平五  马8进7
2. 马二进三  车9平8
...
FEN sequence: <每手 fenAfter 一行，可回放>
```

## 枚举汇总

- `GameStatus`: `loading | playing | paused | over`
- `EndReason`: 如上 6 值
- `Transport`（内部）: `rpc | print`（ADR-002 退路，不对外暴露）

## SSE 事件类型

`state`（全量快照，含 `jevEnabled`）/ `turn` / `thinking` / `retry` / `move` / **`eval`（it-002：{ply, eval} 回填）** / `gameover` / `error` / `log`
