# wardrobe 分册 · 衣橱数据结构（schemaVersion 1）

数据文件 `wardrobe.json`，根结构：

```json
{
  "schemaVersion": 1,
  "persons":   [],
  "items":     [],
  "outfits":   [],
  "notes":     [],
  "wearLogs":  [],
  "wishItems": [],
  "wishOutfits": []
}
```

## Person（角色）

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| id | UUID | ✓ | |
| name | string | ✓ | 显示名，如 "Leo" |
| emoji | string | | 头像 emoji，默认 "🙂" |
| refImageFile | string? | | 形象参考照（包内图片名）；无则 null 或省略 |
| createdAt | long | | epoch 毫秒 |

## Item（单品）

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| id | UUID | ✓ | |
| personId | UUID | ✓ | 所属角色，必须在包内 persons 中存在 |
| category | enum | ✓ | 8 值之一，见下 |
| name | string | ✓ | 必填；如 "白色牛津纺衬衫" |
| color | string | | 如 "白色" |
| desc | string | | 款式一句话："宽松棉质、纽扣领"（拼进生图文案） |
| imageFile | string | ✓ | 包内图片名（**必填**——衣橱单品必有照片） |
| tags | string[] | | ≤10、去重；自由字符串，建议贴预设词 |
| createdAt / updatedAt | long | | |

### category 枚举（磁盘值，一字不差）

`TOP`(上装) · `OUTERWEAR`(外套) · `BOTTOM`(下装) · `DRESS`(连衣裙) ·
`SHOES`(鞋) · `BAG`(包) · `HAT`(帽子) · `ACCESSORY`(其他配饰)

> 槽位顺序 = 上面列出的顺序（ordinal）。

### 标签预设（TagPresets，建议优先从中选）

- 风格：通勤、休闲、运动、约会、度假、正式、简约、复古
- 季节：春、夏、秋、冬、早春、早秋
- 场合：上班、出游、居家、聚会

## Outfit（穿搭）

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| id | UUID | ✓ | |
| personId | UUID | ✓ | |
| itemIds | UUID[] | | 组成的单品，须在包内 items 中存在（悬空会被 app 清洗丢弃） |
| tags | string[] | | 风格/季节/场合 |
| effectImages | OutfitImage[] | | 成品效果图，按加入顺序 |
| createdAt / updatedAt | long | | |

OutfitImage：`{ "file": "包内图片名", "addedAt": long }`

## WearLog（穿搭打卡）

`id` · `personId` · `outfitId`（均须存在）· `at`（穿着时刻）· `createdAt`。
**AI 不要生成打卡**——这是用户的事实记录。

## Note（评论）

`id` · `parentType`（`ITEM` 或 `OUTFIT`）· `parentId`（对应实体 id，须存在）· `text` · `createdAt`。

## WishItem（想买单品）/ WishOutfit（心愿穿搭）

WishItem：`id` · `personId` · `category`（同上枚举）· `name`（必填）· `color` · `desc` ·
`price`(double?) · `url`(商品链接) · `imageFile`(string?，**可空**——无图用品类占位) ·
`tags` · `purchasedAt`(long?，null=未购) · `purchasedItemId`(转正后指向 Item) · `createdAt/updatedAt`。

WishOutfit：`id` · `personId` · `itemIds` · `wishItemIds`（**≥1，不变量**）· `tags` ·
`previewImages`(OutfitImage[]) · `createdAt/updatedAt`。引用须存在。

## 加工守则（AI 适合改什么）

### 适合 AI 改

- `tags`：从照片/描述推断，≤10 去重，优先预设词。
- `desc`：一句话款式描述（面料/版型/领型/厚度），供生图文案拼接。
- `color`：标准色名（白色/藏蓝/卡其…），不要 "有点米白偏黄" 这种含糊值。
- `name`：规范化命名「颜色+品类+款式」，如 "白色牛津纺衬衫"。
- `category`：根据照片归类；拿不准给 `ACCESSORY`。
- 新增 WishItem（种草清单整理）：url/price 从商品截图提取。

### 不要碰

- `id`（已有实体）——改 id = 重复实体。
- `createdAt` / `updatedAt`（已有实体）。
- `wearLogs[]`、`notes[]`（用户的事实与评论）。
- `purchasedAt` / `purchasedItemId`（购买事实回链）。
- persons 的增删（角色是用户身份，AI 不该造人）。

### 引用完整性（validator 强校验）

- item.personId、outfit.itemIds、note.parentId、wearLog.personId/outfitId、
  wishOutfit.itemIds/wishItemIds → 都必须指向包内存在的实体。
- 所有被引用的图片（item.imageFile、effectImages[].file、person.refImageFile、
  wishItem.imageFile、previewImages[].file）必须存在于 images/。

## 示例

见 `examples/wardrobe-sample.zip`（1 角色 + 2 单品 + 1 穿搭 + 1 打卡 + 1 评论 + 1 想买，
图片各 1 张）。`tools/make_examples.py` 可重新生成。
