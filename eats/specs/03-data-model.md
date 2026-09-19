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
│ notes?               │
│ createdAt/updatedAt  │
└──────────────────────┘
派生（不落盘）：lastVisitAt · visitCount · avgVisitRating
```

关系汇总：`Place 1—N Visit`。单用户，无角色实体。

## 字段定义

### Place（食堂）
| 字段 | 类型 | 说明 |
|---|---|---|
| id | String (UUID) | 主键 |
| name | String | 必填，如「巷子深火锅」；自做条目即菜名 |
| kind | PlaceKind | `堂食 RESTAURANT` / `外卖 TAKEOUT` / `自做 HOME` |
| cuisine | String? | 菜系/风味，如「火锅」「日料」 |
| location | GeoLoc? | 经纬度；自做菜通常为空（不上地图） |
| address | String? | 地址文本（手填），仅展示 |
| rating | Int? | 主观综合评分 1–5，可空 |
| tags | List\<String\> | 标签：忌口（辣/香菜）、风味、场景；去重 ≤10 |
| photos | List\<String\> | 门面/菜品照片文件名 |
| notes | String? | 总体笔记（排队严重、错峰去…） |
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

### PlaceKind（枚举）
`堂食 RESTAURANT` / `外卖 TAKEOUT` / `自做 HOME`。三类统一为一个实体（转盘/列表/统计一视同仁），kind 仅影响图标、地图 marker 颜色与「是否有位置」的默认行为（ADR-007）。

### 派生统计（Repository 计算，不落盘）
`lastVisitAt = max(visits.at)`、`visitCount`、`avgVisitRating`（有评分 Visit 的均分）。避免双写不一致（ADR-008）。

### 标签预设（快速点选，可自定义）
- 忌口/口味：辣、清淡、香菜、重油、甜
- 类型：火锅、烧烤、日料、快餐、面食、家常菜
- 场景：聚餐、一人食、夜宵、加班

预设仅影响输入体验；数据里标签是普通字符串，预设清单集中定义在代码常量 `TagPresets`。

## 存储格式

- 元数据：单文件 `files/eats.json`，kotlinx.serialization 序列化全部 Place/Visit；原子写（tmp → rename）；文件头 `schemaVersion`，升级跑迁移函数；成功写入后留 `eats.json.bak`，启动损坏走 bak（与 wardrobe 同模式，ADR-003）。
- 图片：`files/images/*.webp`，文件名 = UUID.webp；导入压缩最长边 1440px、质量 82。

### JSON 结构示例

```json
{
  "schemaVersion": 1,
  "places": [{"id":"pl1","name":"巷子深火锅","kind":"RESTAURANT","cuisine":"火锅",
              "location":{"lat":31.22,"lng":121.44},"address":"某某路12号",
              "rating":4,"tags":["辣","重口味"],"photos":["uuid1.webp"],
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
