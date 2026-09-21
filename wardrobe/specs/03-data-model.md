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

关系汇总：`Person 1—N Item`、`Person 1—N Outfit`、`Item M—N Outfit`（经 `Outfit.itemIds`）、`Item/Outfit 1—N Note`（经 `parentType+parentId`）、`Outfit 1—N OutfitImage`、`Outfit 1—N WearLog`（it-018）。

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

### WearLog（穿搭打卡，it-018 阶段A）
| 字段 | 类型 | 说明 |
|---|---|---|
| id | String (UUID) | |
| personId | String | 所属角色（冗余自 Outfit，写时固化） |
| outfitId | String | 被打卡的穿搭 |
| at | Long | 穿着时刻（默认打卡时刻，可改当日） |
| createdAt | Long | 记录创建时间（与 at 区分：补记） |

同日多条允许（早晚换装）；展示层按日聚合。单品的穿着次数/最后穿着 = 其所在全部穿搭打卡的合并（展示期派生，不落盘）。

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

- 元数据：单文件 `files/wardrobe.json`，kotlinx.serialization 序列化全部 Person/Item/Outfit/Note/WearLog；写入为原子操作（写 tmp → rename）。文件头带 `schemaVersion`，升级时运行迁移函数。it-017 的 `Person.refImageFile` 与 it-018 的 `wearLogs[]` 均为可空默认字段，旧 JSON 缺字段反序列化为空值/空表，属向后兼容变更、未 bump schemaVersion。
- 图片：`files/images/*.webp`（单品照、成品图与形象参考照同目录，文件名 = UUID.webp）。导入时最长边压至 1440px、质量 82。
- 每次成功写入 JSON 后记录 `wardrobe.json.bak`（上一版本），启动时 JSON 损坏则尝试 bak。
- 导出备份（it-024 落地）：数据包 zip = `manifest.json`（packageFormat/app/schemaVersion/exportedAt/generator/counts）+ `wardrobe.json`（SSOT 根原样）+ `images/`。入口 W9「数据」小节与系统分享/打开方式直达；导入默认合并（按 id 实体级覆盖、本地多出保留、永不删数据），替换需二次确认；预检全过才动本地数据、失败零改动；agent 产包放宽图片命名/格式，导入统一归一为 uuid.webp 并重映射引用。agent 侧数据结构说明与校验器：`.agents/skills/data-package/`。（it-001 曾声称「已提供导出」，实为纸面承诺，it-024 一并修正。）

### JSON 结构示例

```json
{
  "schemaVersion": 1,
  "persons": [{"id":"p1","name":"Leo","emoji":"👨","refImageFile":null,"createdAt":0}],
  "items": [{"id":"i1","personId":"p1","category":"TOP","name":"白色牛津纺衬衫",
             "color":"白色","desc":"宽松棉质、纽扣领","imageFile":"uuid1.webp",
             "tags":["通勤","简约"],"createdAt":0,"updatedAt":0}],
  "outfits": [{"id":"o1","personId":"p1","itemIds":["i1","i2"],"tags":["通勤","早秋"],
               "effectImages":[{"file":"uuid9.webp","addedAt":0}],"createdAt":0,"updatedAt":0}],
  "notes": [{"id":"n1","parentType":"ITEM","parentId":"i1","text":"洗后微缩水","createdAt":0}]
}
```

### WishItem（想买单品，it-019）
| 字段 | 类型 | 说明 |
|---|---|---|
| id | String (UUID) | 主键 |
| personId | String | 所属角色 |
| category | WardrobeCategory | 复用 8 类枚举 |
| name | String | 必填 |
| color / desc | String? | 同 Item，可空 |
| price | Double? | 心理价位/标价，可空 |
| url | String? | 商品链接（展示域名，ACTION_VIEW 打开） |
| imageFile | String? | 商品图（可选，截图即可；无图用品类占位） |
| tags | List\<String\> | 复用风格/季节/场合预设 |
| purchasedAt | Long? | 购入时间；null = 未购 |
| purchasedItemId | String? | 转正后指向正式 Item（留档回溯） |
| createdAt / updatedAt | Long | |

### WishOutfit（心愿穿搭，it-019）
`id` · `personId` · `itemIds`（已有单品段）· `wishItemIds`（愿望单品段）· `tags` · `previewImages: List<OutfitImage>`（上身预览图，与 OutfitImage 同构）· `createdAt/updatedAt`。不变量：wishItemIds 至少一件。

### 心愿域机制（ADR-020）
- 混搭预览：WishItem.asSlotItem() 生成 `wish:` 前缀伪 Item 混入 W1 槽位，长图/文案/拼贴按 Item 统一处理，isWishSlot 判定后做愿望标注；伪 id 不落 Outfit（createOutfit 的无效 id 过滤天然防御）。
- 转正：purchaseWishItem 单事务=创建 Item + 回填 purchasedAt/purchasedItemId + 含该件的心愿穿搭把 wishItemIds 移入 itemIds。
- 升级：promoteWishOutfit 要求 wishItemIds 全空，创建 Outfit（tags 继承、previewImages→effectImages）并删除心愿条目。
- 存储：`wardrobe.json` 增 `wishItems[]` / `wishOutfits[]`；缺字段反序列化空表，不 bump schemaVersion。

## 不变量（Repository 层保证）

1. Item/Outfit 的 personId 必须指向存在的 Person；查询永远按当前角色过滤。
2. 删除 Item 时：其 imageFile 物理删除；所在 Outfit 的 itemIds 移除该 id（Outfit 保留）。
3. 删除 Outfit 时：其 effectImages 物理删除；Notes 级联删除；WearLog 级联删除（it-018）。
4. 删除 Person 时：级联删除其全部 Item/Outfit/Note/WearLog 及图片文件，含形象参考照 refImageFile（it-017）；更换参考照时旧文件删除。
5. Outfit.itemIds 中的 id 必须有效（加载时清洗悬空引用）；WearLog 的 outfitId/personId 同样清洗（it-018）。
