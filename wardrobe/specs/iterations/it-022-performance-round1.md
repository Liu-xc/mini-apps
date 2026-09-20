# it-022 · 性能梳理与优化（第一轮，架构评审后专项）

- **状态**：实施中（2026-09-21 夜间，arch-review-fixes worktree）
- **范围**：wardrobe 全屏性能审计 + 低风险优化；不动交互与功能

## 审计结论（2026-09-21 实测代码路径）

**已确认健康、无需改动**：
1. 落盘线程：libs/store `SnapshotStore.writeAtomic` 已 `withContext(Dispatchers.IO)`，所有写操作（含 JSON 编码+tmp/rename/bak）不在主线程。
2. 长图渲染：`WardrobeRecapLongImage.renderTo/render` 与 `OutfitImageComposer.composeToExportFile` 均已 `Dispatchers.IO`。
3. 图片导入/抠图：`ImageFileStore.importFromUri/cutoutTo/decode` 均已 `Dispatchers.IO`。
4. Coil 加载：`AsyncImage` 按布局约束自动降采样（images/ 内 WebP 最长边 1440 已在导入时压缩），列表滚动无全尺寸解码风险。
5. eats `ListScreen` 搜索/过滤/排序主链已 `remember(data, filters…)`。

**本轮修复**（低风险、可测）：
1. `WardrobeScreen`：`itemsOf` + 双层 tag/category 过滤链包 `remember`（此前每次重组重算）。
2. `RecordsScreen`：`outfitsOf` + tag 过滤包 `remember`（同上）。
3. 补 Lazy 列表 key（删除/重排时的 item 状态稳定性）：`ItemDetailScreen` 相关穿搭行、`WardrobeRecapScreen` 最百搭 TOP3、eats `RecapScreen` 最爱 TOP3、eats `PlaceDetailScreen` 到访照片条。

**记录不修**（个人级数据规模，收益为负）：
- `WardrobeRecapScreen` 的 `wardrobeRecap()` 统计在组合期计算（remember 缓存）——12 件单品量级为微秒级。
- eats `PlaceMarkerFactory.sync()` 全量重建 marker——代码注释已声明「个人级规模，重建即可」，成立。
- 心愿/回顾页若干未 remember 的微过滤——量级太小。

## 验证记录

- **R1（2026-09-21）**：修复后 wardrobe 56 单测全绿（含 StaleItemSelector 新增 4 例）；模拟器复验列表滚动/筛选正常。
- **R2（2026-09-21）**：第二轮复审发现长图预览主线程全尺寸解码（~14MB BitmapFactory 在组合期）——已改 Coil 按约束降采样（a684e0b，记入 it-023）；六构建全量复跑全绿。
- **R3（2026-09-21）**：三轮快扫（真实模式+演示模式）零卡顿感知、零崩溃；审计项全部闭环。
