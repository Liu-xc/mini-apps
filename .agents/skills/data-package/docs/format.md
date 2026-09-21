# 包格式 v1（wardrobe / eats 共通）

## zip 结构

```
<app>-backup-YYYYMMDD-HHmm.zip     # 或 agent 命名 <app>-ai-<任务>-<日期>.zip
├─ manifest.json                   # 自描述头（必需）
├─ wardrobe.json | eats.json       # 应用数据，与 app 的 files/ 下完全同构（必需）
└─ images/                         # 图片目录（有图片引用时必需）
   └─ <任意安全文件名>             # webp / jpg / png
```

## manifest.json

```json
{
  "packageFormat": 1,
  "app": "wardrobe",
  "schemaVersion": 1,
  "exportedAt": 1758950000000,
  "generator": "wardrobe 0.6.0",
  "counts": { "items": 42 }
}
```

| 字段 | 规则 |
|---|---|
| packageFormat | 恒为 `1`（当前唯一版本） |
| app | `"wardrobe"` 或 `"eats"`，错配会被对方 app 拒绝 |
| schemaVersion | 恒为 `1`（当前两应用均为 1；写大数会被「请先升级应用」拒绝） |
| exportedAt | epoch 毫秒。app 导出写导出时刻；agent 产包写生成时刻 |
| generator | app 导出写「应用名+版本」；agent 写工具名如 `"agent: gpt-5"` |
| counts | 各集合条数（app 用它展示摘要；validate 会 WARN 不一致） |

## 数据 JSON

- 根结构：wardrobe 见 `wardrobe.md`，eats 见 `eats.md`。键名一字不差。
- **集合键必须显式写**（即使空数组）——省略键在 app 里会解析为空表，静默丢数据。
- 未知字段被忽略（宽容），但不要发明字段。
- 所有 id：UUID v4 小写（`550e8400-e29b-41d4-a716-446655440000` 形态）。
- 所有时间戳：epoch 毫秒整数。

## 图片规则（agent 放宽项）

| 项 | 规则 |
|---|---|
| 文件名 | `[A-Za-z0-9._-]+`，不要中文/空格/路径分隔符 |
| 格式 | webp / jpg / png（魔数校验） |
| 引用 | JSON 字段里写**包内文件名**（不含 `images/` 前缀） |
| 归一化 | app 导入时统一转码为最长边 1440px / 质量 82 的 webp 并改名 uuid.webp——不必预先压缩 |
| 完整性 | 被引用的每张图必须存在于包内；多余的图不会被导入（避免堆垃圾） |

## 导入语义（决定你怎么改数据）

- **合并（默认）**：按 id 对齐——包有 app 无 → 新增；两边都有 → **包内版本整体覆盖**
  （所以打标改字段直接改，不要担心合并逻辑）；app 有包无 → 保留（合并不删数据）。
  要删数据只能用替换模式，或让用户手动删。
- **替换**：整包覆盖。除非用户明示，交付时一律建议合并。
- **幂等**：同一包重复导入安全（再次覆盖为相同内容）。

## 校验与验收

```bash
python3 .agents/skills/data-package/tools/validate.py <包.zip> --app wardrobe  # 或 eats
```

FAIL 项必须清零再交付。常见 FAIL：缺图、枚举拼写、断引用、非 UUID 的 id、
eats 的 PLAY+TAKEOUT 组合 / rating 越界 / 空 url。
