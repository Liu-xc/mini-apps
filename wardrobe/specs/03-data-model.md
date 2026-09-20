# 03 · 数据模型

## 实体关系图（ERD）

```
┌─────────────┐ 1    N ┌───────────────────┐ 1    N ┌──────────────┐
│   Person    │───────▶│       Item        │───────▶│    Note      │
│  角色衣橱主  │  拥有   │  单品              │  评论   │ 自然语言评论   │
├─────────────┤        ├───────────────────┤        ├──────────────┤
│ id          │        │ id                │        │ id           │
│ name        │        │ personId          │        │ parentId     │
│ emoji       │        │ category          │        │ parentType   │──▶ ITEM|OUTFIT
│ refImageFile│─┐     │ name/color/desc   │        │ text         │
│ createdAt   │ │      │ imageFile         │        │ createdAt    │
└──────┬──────┘ │      │ tags: [String]    │        └──────────────┘
       │ 1      └─▶ images/ 下形象参考照文件（可选，it-017，删除角色时级联删除）
       │ 1             └─────────┬─────────┘
       ▼                       N │
┌─────────────┐ 1    N ┌────────┴──────────┐
│   Outfit    │───────▶│  OutfitImage      │
│  穿搭        │  成品图  │  (值对象)          │
├─────────────┤        ├───────────────────┤
│ id          │        │ file (文件名)      │
│ personId    │        │ addedAt           │
│ itemIds[] ──┼─▶ Item（M:N，Item 反查「相关穿搭」）
│ tags[]      │        └───────────────────┘
│ createdAt   │
│ updatedAt   │
└─────────────┘
```

关系汇总：`Person 1—N Item`、`Person 1—N Outfit`、`Item M—N Outfit`（经 `Outfit.itemIds`）、`Item/Outfit 1—N Note`（经 `parentType+parentId`）、`Outfit 1—N OutfitImage`。

## 字段定义

### Person（角色）
| 字段 | 类型 | 说明 |
|---|---|---|
| id | String (UUID) | 主键 |
| name | String | 显示名，如 "Leo" |
| emoji | String | 头像 emoji，如 "👨"，默认 "🙂" |
| refImageFile | String? | 形象参考照文件名（相对 images/，可选；全身照/头像均可，导出长图顶部附给生图 Agent，it-017） |
| createdAt | Long (epoch millis) | |

### Item（单品）
| 字段 | 类型 | 说明 |
|---|---|---|
| id | String (UUID) | |
| personId | String | 所属角色 |
| category | WardrobeCategory | 8 类枚举之一 |
| name | String | 必填 |
| color | String? | 可选 |
| desc | String? | 款式描述，拼进生图文案 |
| imageFile | String | 图片文件名（相对 images/ 目录） |
| tags | List\<String\> | 标签，去重，≤10 个 |
| createdAt / updatedAt | Long | |

### Outfit（穿搭）
| 字段 | 类型 | 说明 |
|---|---|---|
| id | String (UUID) | |
| personId | String | |
| itemIds | List\<String\> | 组成的单品（可跨品类任意组合，允许同类多件如外套+上装） |
| tags | List\<String\> | 风格/季节/场合标签 |
| effectImages | List\<OutfitImage\> | 成品效果图（值对象，按加入顺序） |
| createdAt / updatedAt | Long | |

### OutfitImage（值对象）
`file: String`（文件名） + `addedAt: Long`。

### Note（评论，同一实体挂两种父对象）
`id: String` + `parentType: ITEM|OUTFIT` + `parentId: String` + `text: String` + `createdAt: Long`。

### WardrobeCategory（枚举，固定 8 类）
`上装、外套、下装、连衣裙、鞋、包、帽子、其他配饰`（显示顺序即槽位顺序）。

### 标签预设（快速点选用，可自定义）
- 风格：通勤、休闲、运动、约会、度假、正式、简约、复古
- 季节：春、夏、秋、冬、早秋、早春
- 场合：上班、出游、居家、聚会

预设仅影响输入体验，数据里标签就是普通字符串；预设清单集中定义在代码常量 `TagPresets`。

## 存储格式

- 元数据：单文件 `files/wardrobe.json`，kotlinx.serialization 序列化全部 Person/Item/Outfit/Note；写入为原子操作（写 tmp → rename）。文件头带 `schemaVersion`，升级时运行迁移函数。it-017 的 `Person.refImageFile` 为可空默认字段，旧 JSON 缺字段反序列化为 null，属向后兼容变更、未 bump schemaVersion。
- 图片：`files/images/*.webp`（单品照、成品图与形象参考照同目录，文件名 = UUID.webp）。导入时最长边压至 1440px、质量 82。
- 每次成功写入 JSON 后记录 `wardrobe.json.bak`（上一版本），启动时 JSON 损坏则尝试 bak。
- 导出备份：zip（wardrobe.json + images/），从设置页导出/导入（it-001 提供导出，导入随后续迭代）。

### JSON 结构示例

```json
{
  "schemaVersion": 1,
  "persons": [{"id":"p1","name":"Leo","emoji":"👨","refImageFile":null,"createdAt":0}],
  "items": [{"id":"i1","personId":"p1","category":"上装","name":"白色牛津纺衬衫",
             "color":"白色","desc":"宽松棉质、纽扣领","imageFile":"uuid1.webp",
             "tags":["通勤","简约"],"createdAt":0,"updatedAt":0}],
  "outfits": [{"id":"o1","personId":"p1","itemIds":["i1","i2"],"tags":["通勤","早秋"],
               "effectImages":[{"file":"uuid9.webp","addedAt":0}],"createdAt":0,"updatedAt":0}],
  "notes": [{"id":"n1","parentType":"ITEM","parentId":"i1","text":"洗后微缩水","createdAt":0}]
}
```

## 不变量（Repository 层保证）

1. Item/Outfit 的 personId 必须指向存在的 Person；查询永远按当前角色过滤。
2. 删除 Item 时：其 imageFile 物理删除；所在 Outfit 的 itemIds 移除该 id（Outfit 保留）。
3. 删除 Outfit 时：其 effectImages 物理删除；Notes 级联删除。
4. 删除 Person 时：级联删除其全部 Item/Outfit/Note 及图片文件，含形象参考照 refImageFile（it-017）；更换参考照时旧文件删除。
5. Outfit.itemIds 中的 id 必须有效（加载时清洗悬空引用）。
