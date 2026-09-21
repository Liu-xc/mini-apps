# eats 分册 · 吃啥数据结构（schemaVersion 1）

数据文件 `eats.json`，根结构：

```json
{
  "schemaVersion": 1,
  "places": [],
  "visits":  []
}
```

单用户、无角色实体。派生统计（lastVisitAt/visitCount/avgVisitRating）不落盘，不要发明。

## Place（食堂/去处）

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| id | UUID | ✓ | |
| name | string | ✓ | 如「巷子深火锅」；自做条目即菜名 |
| kind | enum | ✓ | `RESTAURANT`(堂食) / `TAKEOUT`(外卖) / `HOME`(自做) |
| cuisine | string | | 菜系/风味（「火锅」「日料」）；PLAY 类显示为「说明」（如「展览」） |
| location | {lat,lng}? | | 经纬度 double；自做/在家通常为空 |
| address | string | | 地址文本 |
| rating | int? | | **主观评分 1–5**；AI 不要填写 |
| tags | string[] | | ≤10、去重；忌口/口味/类型/场景 |
| photos | string[] | | 门面/菜品照片（包内图片名），按序展示 |
| links | PlaceLink[] | | 外部链接 |
| notes | string | | 总体笔记（排队严重、错峰去…） |
| category | enum | | `EAT`(吃,默认) / `DRINK`(喝) / `PLAY`(玩) |
| wishlistedAt | long? | | 种草时间；**null = 普通条目**；用户自己的状态，AI 不要写 |
| planAt | long? | | 计划去的时间；AI 不要写 |
| createdAt / updatedAt | long | | |

PlaceLink：`{ "url": string（必填非空）, "label": string }`（如「双人套餐」）。
同一条目内 url 去重。

### 组合规则（validator 强校验）

- `PLAY + TAKEOUT` 无效（玩只有出门 RESTAURANT / 在家 HOME）。
- kind 语义按 category 泛化：EAT→堂食/外卖/自做；DRINK→堂食/外送/自调；PLAY→出门/在家。

### 标签预设

- 忌口/口味：辣、清淡、香菜、重油、甜
- 类型：火锅、烧烤、日料、快餐、面食、家常菜
- 场景：聚餐、一人食、夜宵、加班

## Visit（一次吃）

`id` · `placeId`（须在包内存在）· `at`（吃的时间）· `rating`(int? 1–5，主观，AI 不要造) ·
`cost`(double?) · `text`（一句话感想）· `photos`(string[]) · `createdAt`。

## 加工守则

### 适合 AI 改

- `cuisine`：归一到常见菜系词（火锅/烧烤/日料/粤菜/湘菜/面食…）。
- `tags`：从评价/截图推断忌口与场景，优先预设词。
- `name`：规范命名（去掉「【必吃】」「!!!」这类营销噪音）。
- `category` / `kind`：按名称与内容归类（咖啡馆→DRINK+堂食；展览→PLAY+出门）。
- `address` / `location`：从笔记或截图提取整理；经纬度只在高置信时填。
- `links`：从收藏夹/分享链接提取 url + label；url 必须是完整可打开链接。
- 新增 Place：整理收藏夹/探店清单的典型形态；无图可以不填 photos。

### 不要碰

- `rating` / `cost`——主观与隐私事实。
- `at` / `createdAt`（visit 的时间线）与已有 visits 的增删。
- `wishlistedAt` / `planAt`——用户的种草/安排状态。
- 已有实体的 `id`。

## 示例

见 `examples/eats-sample.zip`（2 家 + 2 笔记录 + 1 张 jpg 图）。
`tools/make_examples.py` 可重新生成。
