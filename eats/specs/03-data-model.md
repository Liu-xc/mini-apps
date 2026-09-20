# 03 · 数据模型

## 实体关系图（ERD）

```
┌──────────────────────┐ 1    N ┌──────────────────┐
│        Place         │───────▶│      Visit       │
│  食堂（堂食/外卖/自做）  │  一次吃  │  一条吃的记录      │
├──────────────────────┤        ├──────────────────┤
│ id                   │        │ id               │
│ name                 │        │ placeId          │
│ kind                 │        │ at（吃饭时间）      │
│ cuisine?             │        │ rating?          │
│ location?(lat,lng)   │        │ cost?            │
│ address?             │        │ text?            │
│ rating?              │        │ photos[]         │
│ tags[]               │        │ createdAt        │
│ photos[]             │        └──────────────────┘
│ links[]              │
│ notes?               │
│ createdAt/updatedAt  │
└──────────────────────┘
派生（不落盘）：lastVisitAt · visitCount · avgVisitRating
```

关系汇总：`Place 1—N Visit`。单用户，无角色实体。

## 字段定义

### Place（食堂/去处）
| 字段 | 类型 | 说明 |
|---|---|---|
| id | String (UUID) | 主键 |
| name | String | 必填，如「巷子深火锅」；自做条目即菜名 |
| kind | PlaceKind | `堂食 RESTAURANT` / `外卖 TAKEOUT` / `自做 HOME`；语义泛化见 ADR-012（显示文案按分类适配） |
| category | PlaceCategory | 一级分类（it-008）：`吃 EAT` / `喝 DRINK` / `玩 PLAY`；默认 EAT，旧 JSON 缺字段反序列化 EAT 零迁移 |
| cuisine | String? | 菜系/风味（PLAY 类 UI 显示为「说明」，如「展览」） |
| location | GeoLoc? | 经纬度；自做/在家条目通常为空（不上地图） |
| address | String? | 地址文本（手填），仅展示 |
| rating | Int? | 主观综合评分 1–5，可空 |
| tags | List\<String\> | 标签：忌口（辣/香菜）、风味、场景；去重 ≤10 |
| photos | List\<String\> | 门面/菜品照片文件名 |
| links | List\<PlaceLink\> | 外部链接（美团/点评分享链接等），url 去重 |
| notes | String? | 总体笔记（排队严重、错峰去…） |
| wishlistedAt | Long? | 种草时间（it-008）：非空 = 愿望条目；记一笔落账自动清空（拔草，可撤销） |
| planAt | Long? | 计划去的时间（it-008 阶段C，仅愿望提供入口），可空；当天抽签页顶部提示、列表「最近的安排」置顶 |
| createdAt / updatedAt | Long (epoch millis) | |

### Visit（一次吃）
| 字段 | 类型 | 说明 |
|---|---|---|
| id | String (UUID) | |
| placeId | String | 所属食堂 |
| at | Long | 吃的时间（默认创建时刻，可改） |
| rating | Int? | 本次评分 1–5 |
| cost | Double? | 本次花费（元） |
| text | String? | 一句话感想 |
| photos | List\<String\> | 本次照片文件名 |
| createdAt | Long | 记录创建时间（与 at 区分：补记昨天的吃） |

### GeoLoc（值对象）
`lat: Double` + `lng: Double`。

### PlaceLink（值对象）
`url: String` + `label: String?`（如「双人套餐」「店铺主页」）。来源徽标（美团/大众点评/其他）由 `LinkSource.detect(url)` 在展示时派生，不落盘（ADR-009）。

### PlaceKind（枚举）
`堂食 RESTAURANT` / `外卖 TAKEOUT` / `自做 HOME`。三类统一为一个实体（转盘/列表/统计一视同仁），kind 仅影响图标与「是否有位置」的默认行为（ADR-007）；marker 颜色自 it-008 起改由 category 驱动。

### kind 语义泛化（it-008，ADR-014）
枚举值不变（磁盘零迁移），从「怎么吃」泛化为「在哪进行」，显示文案按 category 适配：

| category | kind 显示文案 | kind 选项 |
|---|---|---|
| EAT 吃 | 堂食 / 外卖 / 自做 | 全部 |
| DRINK 喝 | 堂食 / 外送 / 自调 | 全部 |
| PLAY 玩 | 出门 / 在家 | 不含 TAKEOUT（PLAY+TAKEOUT 为无效组合，录入 UI 不提供） |

### PlaceCategory（枚举，it-008）
`吃 EAT` / `喝 DRINK` / `玩 PLAY`。一级分类驱动：抽签分类 chips 与动态标题（今天干啥/吃啥/喝啥/玩啥）、地图 marker 与图例三色（吃=红 / 喝=琥珀 / 玩=紫，ADR-014）、列表分类筛选、记录按钮与结果条文案（就吃/就喝/就去，安排）。

### 愿望与安排（it-008）
- 愿望 = `wishlistedAt != null`；W3 筛选「只看愿望」与排序「种草时间」、W1「只抽愿望」开关共用该判定。
- 拔草：愿望条目落账（addVisit）后自动清空 wishlistedAt，snackbar「已移出愿望清单 · 撤销」恢复原值（ViewModel 层实现）。
- 安排：planAt 仅对愿望条目提供入口；「最近的安排」小节显示未来计划（升序，最多 3 条），过期静默回普通愿望序。

### 派生统计（Repository 计算，不落盘）
`lastVisitAt = max(visits.at)`、`visitCount`、`avgVisitRating`（有评分 Visit 的均分）。避免双写不一致（ADR-008）。

### 标签预设（快速点选，可自定义）
- 忌口/口味：辣、清淡、香菜、重油、甜
- 类型：火锅、烧烤、日料、快餐、面食、家常菜
- 场景：聚餐、一人食、夜宵、加班

预设仅影响输入体验；数据里标签是普通字符串，预设清单集中定义在代码常量 `TagPresets`。

## 存储格式

- 元数据：单文件 `files/eats.json`，kotlinx.serialization 序列化全部 Place/Visit；持久化机制由 libs/store 的 `SnapshotStore` 承担（ADR-010）：原子写（tmp → rename）+ `.bak` + 三级恢复 + 逐版本迁移链；文件头 `schemaVersion` 不变，**磁盘格式与 it-005 前完全兼容**（ADR-003 语义原样上收 SDK）。
- 图片：`files/images/*.webp`，文件名 = UUID.webp（SDK `FileMediaStore` 管理）；导入压缩最长边 1440px、质量 82。演示模式下图片目录切换至 `cacheDir/mock-images`（it-006）。

### JSON 结构示例

```json
{
  "schemaVersion": 1,
  "places": [{"id":"pl1","name":"巷子深火锅","kind":"RESTAURANT","cuisine":"火锅",
              "location":{"lat":31.22,"lng":121.44},"address":"某某路12号",
              "rating":4,"tags":["辣","重口味"],"photos":["uuid1.webp"],
              "links":[{"url":"https://s.dianping.com/abcd","label":"双人套餐"}],
              "notes":"排队严重，错峰去","createdAt":0,"updatedAt":0}],
  "visits": [{"id":"v1","placeId":"pl1","at":1758300000000,"rating":5,"cost":128.0,
              "text":"毛肚绝了","photos":["uuid2.webp"],"createdAt":1758300000000}]
}
```

## 不变量（Repository 层保证）

1. Visit.placeId 必须指向存在的 Place；查询结果携带派生统计。
2. 删除 Place：级联删除其全部 Visit；双方 photos 文件物理删除。
3. 删除 Visit：其 photos 物理删除，Place 保留。
4. photos 引用的文件缺失时加载阶段清洗悬空引用（不崩溃）。
5. location 为空的 Place 不进入地图数据集。
6. links 的 url 不可为空且保存时去重；label 可空。

## 本地提醒设置与抑制标记（it-007 阶段B，KV，不进 eats.json）

- 存储：DataStore `recap_prefs`（与转盘过滤的 `eats_prefs` 分文件，避免同文件多 DataStore 冲突）。
- 键：`reminder_enabled: Boolean`（默认 false）、`reminder_days: Int`（默认 90）、`last_notified_place_id: String?`（重复抑制：候选未用尽前不重推同一家）。
- 提醒候选规则（MemoryCandidateSelector，纯函数）：`visitCount ≥ 3` 且有效评分（综合评分优先、缺失回退 Visit 均分）`≥ 4` 且 `now - lastVisitAt ≥ days`；每次至多随机取 1 家。
