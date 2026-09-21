# it-012 · 结构化数据包：导出 / 导入 / agent 加工回流（eats 侧）

- 状态：**提案，待 Leo 确认后开工**
- 用户故事：US-16
- 姊妹迭代：wardrobe it-024（**包格式 v1 与 libs/store 0.2.0 codec 均以 it-024 为准**，本迭代消费复用；若两迭代同批实施，先落 codec）
- 关联：ADR-015、`.agents/skills/data-package/`（补 eats 分册）
- 提出日期：2026-09-21

## 背景与动机

与 wardrobe it-024 同源：Leo 要把「收集一大批原始数据 → 交给 AI 打标加工 → 整包导回」的链路跑通。对 eats 的典型场景：把收藏夹 / 外卖订单截图 / 探店笔记整理成 Place+Visit 打标数据包导入（菜系、忌口标签、位置、链接），或对既有食堂批量补 cuisine/tags。

现状（已核实）：`files/eats.json`（schemaVersion=1，SSOT 经 libs/store）+ `files/images/*.webp`；**无任何导出/导入能力**（eats 从未承诺过备份，本迭代为净新增）。派生统计（lastVisitAt/visitCount/avgVisitRating）不落盘，导入无需关心。

## US-16 数据包导入导出

> 作为食客，我能把全部食堂与记录（含照片）导出成一个结构化数据包，交给 AI 或其他工具加工后导回，实现批量打标、迁移与备份。

## 数据包格式 v1

与 wardrobe it-024 定义的共通契约完全一致（manifest.json + `eats.json` + `images/`，放宽项与归一化同），此处不重复。差异点仅：

- manifest：`"app": "eats"`，counts 为 `{"places": N, "visits": M}`；建议包名 `eats-backup-YYYYMMDD-HHmm.zip`。
- 数据文件为 eats.json 原样（SSOT 根，`places[]` / `visits[]`）。

## 导入语义

预检、合并、替换、原子性、演示模式约束全部同 it-024（同 codec 实现）。eats 特有校验：

1. **枚举字面量**：`kind ∈ {RESTAURANT, TAKEOUT, HOME}`、`category ∈ {EAT, DRINK, PLAY}`（磁盘存英文枚举名，UI 才显示中文）。
2. **无效组合拒绝**：`PLAY + TAKEOUT`（录入 UI 都不提供，包里出现视为脏数据）。
3. `rating` 若非空须 1–5；`links[].url` 非空且实体内去重。
4. 合并：places / visits **按 id 合并**，包内实体整体覆盖同 id 本地实体，本地多出保留；合并结果跑现有清洗（visit.placeId 悬空、photos 悬空）后单事务 commit。
5. 愿望字段 `wishlistedAt` / `planAt` 随实体整体覆盖（属于该实体数据，无特殊处理）。

## UI（W7 统计回顾页）

交互线框与流程以 wardrobe it-024 的「交互线框（评审稿 D0–D7）」为准，两 app 共用同一套；eats 差异见其注记 12：入口挂 W7「回忆提醒设置」卡之后，计数行改为「＋新增 X 家 · ↻更新 Y 家」，错误样例含 `PLAY+TAKEOUT` 无效组合，④b 系统直达入口（微信「用其他应用打开」/ 文件管理器「分享」）同样注册、选择器里显示「吃啥」，其余同构（合并默认 / 替换二次确认 / 预检失败零改动 / 演示模式置灰）。

## agent skill 分册（`.agents/skills/data-package/`）

skill 骨架与 validator 由 it-024 先行落地；本迭代交付：

- `docs/eats.md`：Place/Visit 字段表、kind/category/kind 语义泛化对照（PLAY 无 TAKEOUT）、GeoLoc 规范（自做/在家条目通常留空）、加工守则——
  - 适合 AI 改：`cuisine`、`tags`（≤10 去重、贴忌口/类型/场景预设词汇）、`name` 规范化、`category` 归类、`address`、`links` 补链接（label 建议）。
  - **不要碰**：`rating`（主观评分）、`cost`、`at`/`createdAt`（记录时间线）、`wishlistedAt`/`planAt`（用户自己的种草/安排状态）、已有实体 `id`。
- `examples/eats-sample.zip`：最小合法包（含 1 张非 webp 图片验证放宽项）。
- `tools/validate.py` 补 eats 规则组（`--app eats`）。

## 验收标准

1. **备份回放**：造数据（含愿望条目与安排）→ 导出 → 清数据 → 导入替换 → 列表/地图/抽签/回顾/愿望全部正常，photos 显示。
2. **合并回流**：构造打标包（改若干 place 的 cuisine/tags + 新增 2 家各带 1 张 jpg + 补 1 条 visit）→ 导入合并 → 更新生效、新增可见、其余保留、无重复。
3. **错包防御**：wardrobe 包 / 坏 JSON / 缺图 / PLAY+TAKEOUT / rating=9 → 全部拒绝、原因可读、本地零改动。
4. **演示模式**置灰。
5. **skill 实测**：另一 agent 会话仅凭 skill 文档产出 eats 打标包 → validate.py PASS → 导入成功。
6. **单测**：`mergeEats`（覆盖/新增/保留/PLAY+TAKEOUT 拒绝/visit 悬空清洗）、eats 包预检规则。
7. specs 同步：00（若价值流有增）/01（US-16）/02（W7 数据小节）/03（存储格式补包格式）/06（ADR-015）；CHANGELOG.md 记一行。

## 影响范围

- `eats/app`：`di/AppContainer`（codec 装配）、`data/repo`（import/export service + merge）、`domain`（mergeEats 纯函数）、`ui/recap`（数据小节）、manifest（zip intent-filter）+ MainActivity（直达导入路由）、单测。
- `.agents/skills/data-package/`：docs/eats.md + validate.py eats 规则 + examples/eats-sample.zip。
- 依赖：libs/store 0.2.0（it-024 先行）。

## 范围外

同 it-024：云同步、定时备份、加密、字段级深合并、桌面端工具。

## 验证记录

（实施后回填）
