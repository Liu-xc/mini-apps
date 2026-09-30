# 03 — 数据模型

## 实体

### Game（玩法枚举，代码即真源）

| 值 | 名称 | 主区 | 特号区 |
|---|---|---|---|
| `SSQ` | 双色球 6+1 | 6/33（1..33） | 1/16（1..16） |
| `DLT` | 大乐透 5+2 | 5/35（1..35） | 2/12（1..12） |

复式（demo 限档，非官方上限）：SSQ 主区 7–12；DLT 主区 6–10；特号区恒单式
（SSQ 1 个蓝球 / DLT 2 个后区）。注数 = C(m1, n1) × C(m2, n2)。

### Ticket（票，JSON 持久化 `filesDir/tickets.json`）

| 字段 | 类型 | 说明 |
|---|---|---|
| id | String | UUID |
| game | Game | 玩法 |
| createdAt | Long | epoch ms |
| seed | String | SHA-256 前 16 hex（种子指纹） |
| take | Int | 批次（1 起，「再换一批」+1） |
| zone1 / zone2 | List<Int> | 排序去重后的选号 |
| targetIssue | String | 生成时指向的最新开奖期号 |

序列化：kotlinx-serialization JSON；未知字段忽略（前向兼容，LESSONS 架构条）。
写入：tmp → rename 原子落盘。

### DrawResult（开奖结果）

| 字段 | 类型 | 说明 |
|---|---|---|
| game | Game | 玩法 |
| issue | String | 期号：`yyyyNNN`（年内顺序第 N 个开奖日） |
| date | String | 开奖日 `yyyy-MM-dd` |
| zone1 / zone2 | List<Int> | 中奖号码（排序） |
| demo | Boolean = true | 本期恒 true（演示数据，UI 必须露出标注） |

### 验票输出

- `Hit(zone1, zone2)`：单注命中数。
- `PrizeLevel`：SSQ 一~六等；DLT 一~九等（官方奖级表，见 06 ADR-004）。
- `TicketVerdict`：`combos` 总注数、`byLevel: Map<PrizeLevel, Int>` 各级注数、
  `best: PrizeLevel?`、`totalWinning: Int`。

## 派生规则（纯函数，JVM 单测覆盖）

- **期号**：从当年 1 月 1 日起逐日数玩法开奖日（SSQ 二/四/日；DLT 一/三/六）到目标日，
  序号 `yyyy + %03d`。当日非开奖日 → 最新期 = 最近一个过去的开奖日。
- **演示开奖号**：`seed = SHA256(game + issue)` → 确定性 PRNG 出号 → 排序。同 (game, issue)
  恒同结果。
- **种子指纹**：图片解码 64×64 ARGB_8888（软件位图）→ 像素字节 SHA-256 → hex。
- **生成**：`rng = PRNG(SHA256(seed + game + m1 + m2 + take))` → 主区 n1 个不重复 + 特号区
  n2 个不重复 → 排序。

## 存储一览

| 数据 | 介质 | 位置 |
|---|---|---|
| 票夹 | JSON 文件 | `filesDir/tickets.json`（tmp→rename） |
| 导出购票图 | MediaStore | `Pictures/拾彩/*.png` |
| 选中的种子图 | 不落盘 | 会话内存（生成即解码出指纹） |
