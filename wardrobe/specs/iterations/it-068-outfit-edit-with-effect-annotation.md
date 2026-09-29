# it-068 · 穿搭详情放开调整单品 + 成品图标注

状态：**已实施**（2026-09-29；方案由 Leo 2026-09-29 拍板「放开编辑 + 标注」，替代原挂账 it-067 规划项）

> 编号勘注：原 review 规划的 it-067 位次已被 [it-067-auto-versioning](it-067-auto-versioning.md) 占用，本迭代顺延为 it-068。

## 背景与动机

it-049 为防「旧效果图被误读成新组合」，把「调整单品」限制为无成品图的穿搭——但代价是换季改一件只能「创建副本」（丢打卡/评论）或删成品图（丢图），高频刚需被堵死。Leo 拍板：**放开编辑，用标注承担防误导**。

## 涉及的用户故事

- **US-48 穿搭详情调整与副本（增补）**：有成品图的穿搭同样可调整单品；调整后成品图区标注「成品图为调整前组合」，录入新成品图后标注解除。

## 验收标准

- W7 更多菜单「调整单品」**恒可用**（不再随有无成品图显隐）；编辑态替换/移除/补齐语义不变。
- 已有成品图的穿搭在**单品变更保存后**，成品图区显示轻标注「成品图为调整前组合」（`labelSmall + inkFaint`，不抢主体、不引入新色彩）。
- 标注解除条件：录入新成品图（新图即当前组合的效果）；成品图全部删除后标注自然失效。
- 标注跨会话持久（`Outfit.effectStale`，JSON 默认值向后兼容，旧数据读入为 false）。
- 创建副本不带成品图与标注（既有语义不变）。
- `./gradlew :app:testDebugUnitTest :app:assembleDebug -PdemoDefault=true` 全绿。

## 实施方案

1. `Entities.kt`：`Outfit` 增 `effectStale: Boolean = false`（kotlinx 默认值，旧快照兼容）。
2. `WardrobeRepositoryImpl.addEffectImage`：追加成品图时清 `effectStale`（单一真源）。
3. `AppViewModel.updateOutfitItems`：单品变更且已有成品图时置 `effectStale = true`，toast 带「成品图为调整前组合」提示。
4. `OutfitDetailScreen.kt`：菜单去掉 `effectImages.isEmpty()` 条件；成品图轮播指示点下增标注行（`effectStale` 时显示）。
5. spec 同步：01（US-48）、02（W7 注记）、03（Outfit 字段）；CHANGELOG。

## 影响范围

- **代码**：`domain/model/Entities.kt`、`data/repo/WardrobeRepositoryImpl.kt`、`ui/AppViewModel.kt`、`ui/records/OutfitDetailScreen.kt`。
- **常青 spec**：`01-user-stories.md`、`02-wireframes.md`、`03-data-model.md`。
- **不涉及**：存储格式版本迁移（默认值兼容）、W8 列表、导出域。

## 验证记录

**构建与测试（2026-09-29）**

- `./gradlew :app:testDebugUnitTest :app:assembleDebug -PdemoDefault=true`：BUILD SUCCESSFUL，91 测全过（含本轮新增 2 测：`addEffectImageClearsEffectStale`（录新图清标注）、`legacyOutfitJsonWithoutEffectStaleReadsFalse`（旧快照缺字段读入默认 false，向后兼容守门））。

**AVD 走查（wardrobe_test / emulator-5558）**

- ✅ **核心行为**：有成品图的穿搭（W7 · 2026/09/24）更多菜单出现「调整单品」（uiautomator dump 实证；旧代码此处不渲染），点击进入编辑态（取消调整/保存调整/「点衣物可替换…」/「添加单品」齐全）——it-049 限制解除实证。
- ⏳ **标注渲染链路**（替换/移除单品 → 保存 → 「成品图为调整前组合」出现；录新图消失）：本轮 AVD System UI 两次 ANR + 多步 tap 链不稳未取到干净帧；已由上述单测（置位/解除/兼容三态中的两态）+ 代码路径评审兜底，**标注视觉形制留待复验**（一行 `labelSmall + inkFaint`，无复杂布局）。
- 免费回归：W6 导出面板（it-061/066 预览展开、表单、「另要求」记忆）经 W7「复制长图」通道实测正常。
