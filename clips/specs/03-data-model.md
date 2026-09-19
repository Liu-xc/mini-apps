# 03 · 数据模型

## 实体（单实体应用）

```
┌─────────────────────────┐
│       ClipEntry         │
├─────────────────────────┤
│ id: String (UUID)       │
│ text: String (≤10k 字符) │
│ typeHint: TypeHint      │
│ pinned: Boolean         │
│ source: Source          │
│ createdAt: Long         │
│ lastUsedAt: Long?       │
│ useCount: Int           │
└─────────────────────────┘
```

### ClipEntry 字段
| 字段 | 类型 | 说明 |
|---|---|---|
| id | String (UUID) | 主键 |
| text | String | 原文；超过 10,000 字符入库前截断：保留首尾各 ~4,500 字符，中间以 `…(已截断 N 字)…` 连接 |
| typeHint | TypeHint | 本地启发式识别，入库时计算（见下） |
| pinned | Boolean | 置顶；容量淘汰豁免 |
| source | Source | `MAC_POLL` / `ANDROID_OPEN` / `SHARE`，来源标记（仅展示用） |
| createdAt | Long (epoch ms) | 首次捕获时间；去重复用时刷新为当前 |
| lastUsedAt | Long? | 最近一次「复制回」的时间 |
| useCount | Int | 被使用（捕获去重复用 + 显式复制回）次数 |

### TypeHint（启发式，按序判定，命中即止）
| 值 | 判定（概要） | 展示 |
|---|---|---|
| URL | `https?://` 开头且可解析为 URI | 🔗 徽章 |
| EMAIL | 恰好一个 `@` 且两侧均为合法字符、右侧含 `.` | ✉ 徽章 |
| PHONE | 仅数字/`+`/`-`/空格，7–15 位有效字符 | 📞 徽章 |
| COLOR | `#RGB` / `#RGBA` / `#RRGGBB` / `#RRGGBBAA` | 色块预览（取 text 本身颜色） |
| JSON | 首字符 `{`/`[` 且可被 kotlinx.serialization 解析 | `{}` 徽章 |
| CODE | 多行 且（行首空白率 / 分号 / 括号密度）超阈值 | `</>` 徽章 |
| TEXT | 其余（默认） | 无徽章 |

启发式是 commonMain 里的纯函数，单测覆盖边界；识别错误仅影响徽章展示，不影响数据本体。

## 存储格式

- 单文件 `files/clips.json`，kotlinx.serialization；原子写（tmp → rename），每次成功写后留 `clips.json.bak`（上一版本），启动时损坏则回退 bak。模式与 wardrobe 的 JsonFileStore 一致（见其 ADR-002）。
- 文件头 `schemaVersion`，升级时运行迁移函数。
- 规模估算：默认 50 条 × 平均 0.3KB ≈ 15KB；上限 1000 条的极端情况（条条 10k 字符）≈ 10MB——仅理论峰值，正常使用远小于此。无图片，无其他文件。

### JSON 结构示例

```json
{
  "schemaVersion": 1,
  "entries": [
    {"id":"c1","text":"https://example.com/a-very-long-url",
     "typeHint":"URL","pinned":false,"source":"MAC_POLL",
     "createdAt":1758300000000,"lastUsedAt":null,"useCount":0},
    {"id":"c2","text":"#3E6FF4","typeHint":"COLOR","pinned":true,
     "source":"ANDROID_OPEN","createdAt":1758200000000,
     "lastUsedAt":1758350000000,"useCount":3}
  ]
}
```

## 不变量（Repository 层保证）

1. `text` 非空且非纯空白（捕获层过滤，Repository 兜底拒绝）。
2. **去重**：入库文本与任一现存条目完全相同 → 复用该条目：`createdAt` 刷新为当前、`useCount+1`、排到最前，不新增条目（ADR-007）。
3. **淘汰**：任何写入后条目数 > 容量上限 → 按 `createdAt` 升序淘汰未置顶条目直至 ≤ 上限；置顶条目永不淘汰（即使置顶数超过上限也全部保留）。
4. 排序口径：置顶组在前（组内按 `createdAt` 降序），普通组随后（同样降序）；日分组头（今天/昨天/更早）是 UI 层展示分组，不改变存储顺序。
5. 删除仅动 JSON 记录，无关联文件需要清理。
