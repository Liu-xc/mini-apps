# it-060 · 演示衣橱 mock 数据与透明单品素材扩容

> 状态：**已完成**（2026-09-29）
> 来源：Leo 需求「往衣橱 APP 里再尽可能补充丰富的 mock 数据及图片；衣物不要背景，需要做成周边透明的 png」
> 前置：it-059（W4 录入表单点选化 + 演示人物数据）正在实施；本迭代不覆盖其代码或未提交素材。

## 背景与动机

当前 APK 内置 mock 已覆盖 2 个角色、8 个品类、36 件单品和 9 套穿搭，但品类密度不均：连衣裙、帽子、配饰的样本偏少，衣橱网格与分类筛选的真实浏览感仍不足。用户希望演示模式更像一套可长期走查的丰富衣橱，并且新增衣物图片能够直接融入拼贴、导出长图和卡片衬纸，而不是带白色/灰色商品图背景。

## 用户故事

- **US-60a**：作为评审者，我打开演示模式后能浏览到数量充足、风格和季节更丰富的衣物，八个品类都不显得空。
- **US-60b**：作为评审者，我在分类筛选、搭配槽位和单品详情中看到的新增衣物边缘干净，周边透明，不会出现矩形背景块。
- **US-60c**：作为开发者，我构建 APK 后无需联网或额外拷贝素材，mock JSON 与图片引用完整且可重复验证。

## 方案

### A · 扩充数据集

- 以 `assets/mock/wardrobe.json` 为演示模式单一数据源，保留现有实体 id、人物、穿搭、评论、心愿和时间语义。
- 新增约 **32 件单品**，总量从 36 扩至约 68 件；按 TOP / OUTERWEAR / BOTTOM / DRESS / SHOES / BAG / HAT / ACCESSORY 均衡补齐，覆盖春夏秋冬、通勤/休闲/运动/约会/度假/正式等标签。
- 新增至少 6 套穿搭组合，覆盖新增品类与多角色；不生成 `wearLogs`，不伪造用户事实评论。必要时只新增演示用途的穿搭标签与空 effectImages。
- 新增少量带透明底的 wishItems 图片（若不增加 APK 负担），验证心愿单图片展示路径；不改变现有购买状态与回链。

### B · 透明 PNG 素材

- 每件新增衣物使用单独 PNG，主体居中、完整可见、周边是真透明 alpha；不使用白底、灰底、棋盘格或带阴影的矩形画布。
- 统一导出为 RGBA PNG，最长边约 512–768px；允许主体保留自然柔和边缘，但透明区 alpha 必须真实为 0。
- 图片命名使用安全 ASCII 文件名，如 `item-37.png`；JSON 只引用包内文件名。
- 生成后逐张做程序化门禁：RGBA、存在透明像素、透明像素占比合理、非透明主体不被裁切、PNG 可解码；抽样检查浅色衣物边缘无白框。

### C · 保持现有演示链路

- bump `AppContainer.MOCK_ASSET_REVISION`，让已安装版本刷新同名内置资源；不删除或覆盖演示操作生成的 UUID 临时图片。
- 同步 `MockWardrobeData.create()` 的 JVM 种子，或明确保留其历史种子并补测试说明；正式演示入口继续从 `assets/mock/wardrobe.json` 解析。
- 不变更 schemaVersion、真实衣橱存储、导入导出协议和领域模型。

## 验收标准

1. `assets/mock/wardrobe.json` 可解析，新增 item/outfit/wishItem 的 UUID、personId、itemIds 和图片引用全部完整；旧实体 id 不变。
2. mock 单品总数达到约 68 件，八个品类均有新增内容；至少 2 个角色均能看到新增衣物；新增标签覆盖至少 4 个季节/场合维度组合。
3. 所有新增衣物 PNG 均为 RGBA，透明像素占比 > 0，主体未被裁切；不得存在被 JSON 引用但不存在的图片，也不得存在缺少 alpha 的新增衣物图。
4. 通过 `.agents/skills/data-package/tools/validate.py` 生成的 mock 数据包校验：`PASS`，无 FAIL；若打包交付，zip 内含 `manifest.json`、`wardrobe.json` 和 `images/`。
5. `./gradlew :app:testDebugUnitTest :app:assembleDebug` 通过，APK 内含新增 JSON 与全部图片；演示模式冷启动能显示新增资源。
6. 至少完成一次分类筛选、搭配槽位、单品详情和穿搭详情的模拟器走查，确认新增素材没有矩形背景块或明显白边。
7. 回填本文件「验证记录」，同步受影响的 `01-user-stories.md`、`02-wireframes.md`、`05-design-system.md`（若有视觉约束新增）与根/应用 `CHANGELOG.md`；若发现跨任务通用教训，再按规则更新 `LESSONS.md`。

## 影响范围

- `wardrobe/app/src/main/assets/mock/wardrobe.json`
- `wardrobe/app/src/main/assets/mock/item-*.png` 与可选 effect/wish 图片
- `wardrobe/app/src/main/java/com/leo/wardrobe/di/AppContainer.kt`
- `wardrobe/app/src/main/java/com/leo/wardrobe/data/mock/MockWardrobeData.kt`（若 JVM 种子需要同步）
- 可能新增 `wardrobe/tools/` 下的素材 alpha/引用检查脚本
- 迭代 spec、相关常青 spec、CHANGELOG；不改真实数据协议与 schemaVersion

## 验证记录

- 已使用内置 `image_gen` 默认模式生成 32 张独立单品 PNG（`item-37.png`～`item-68.png`），提示词统一约束为「isolated wardrobe mock asset / genuinely transparent background / no mannequin, person, hanger, floor, backdrop, drop shadow, text or watermark」；生成后保留于 `wardrobe/app/src/main/assets/mock/`，并统一缩放至最长边 ≤768px。
- `wardrobe.json` 现含 2 角色、68 单品、15 套穿搭、5 条评论、4 件心愿单品、1 套心愿穿搭；八个品类均覆盖，新增 item/outfit id 与图片引用检查通过。
- 新增 PNG 程序化检查：全部可解码、RGBA、存在 alpha=0 透明像素、最长边 ≤768px，未发现缺失引用。
- 数据包：`wardrobe/dist/wardrobe-ai-mock-20260929.zip` 通过 `.agents/skills/data-package/tools/validate.py --app wardrobe`，结果 `PASS`、0 警告。
- Android：`./gradlew :app:testDebugUnitTest :app:assembleDebug` 与 `./gradlew :app:assembleDebug -PdemoDefault=true` 均 `BUILD SUCCESSFUL`；APK 静态清单确认含 `wardrobe.json` + 32 张新增 PNG。
- 模拟器：安装体验包后冷启动 W1，组合正常加载；进入 W3 并向下滚动，看到新增「酒红色针织 Polo」等单品，透明图层融入白色卡面，无矩形背景块。证据见 [it060 报告](../../../reports/2026-09-29-it060/README.md)。
- `MockWardrobeData.create()` 保留为 17 件 JVM 历史测试夹具，代码注释已明确；正式演示入口继续以 `assets/mock/wardrobe.json` 为唯一数据源。
