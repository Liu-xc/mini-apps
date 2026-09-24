# it-032 · 演示数据集扩充与图片随 APK 编译

- **状态**：已完成（2026-09-24）
- **来源**：衣橱 mock 数据生成需求；要求生成后的图片与信息随应用编译进 APK

## 背景与动机

原演示模式只有 17 件旧素材，无法充分覆盖衣橱的多角色、8 品类、心愿域与穿搭效果图场景。
本轮将生成的结构化数据包作为演示模式的单一数据源，并把对应图片放进 `assets/mock/`，保证
APK 离线安装后即可走查，不依赖外部 zip 或网络。

## 用户故事

- **US-32a**：作为评审者，我打开 DEBUG 演示模式时能看到内容充分且图片与名称一致的衣橱数据。
- **US-32b**：作为开发者，我构建 APK 后无需额外拷贝素材，演示模式即可读取内置 JSON 与图片。

## 验收标准

- `assets/mock/wardrobe.json` 与内置图片随 `assembleDebug` 进入 APK。
- 演示数据包含 2 角色、36 单品、9 套穿搭、5 条评论、4 个心愿单单品、1 套心愿穿搭。
- 图片覆盖 TOP / OUTERWEAR / BOTTOM / DRESS / SHOES / BAG / HAT / ACCESSORY 八类，且引用完整。
- `./gradlew assembleDebug testDebugUnitTest` 通过。

## 影响范围

`app/src/main/assets/mock/`、`data/mock/MockWardrobeData.kt`、`di/AppContainer.kt`；不改变真实衣橱
数据存储与导入导出协议。

## 验证记录（2026-09-24）

- 数据包 validator：`wardrobe-ai-mock-20260924.zip` PASS，0 警告。
- 构建/测试：`./gradlew assembleDebug testDebugUnitTest` SUCCESS。
- APK 清单确认新增 40 个资源：`wardrobe.json`、36 张单品 PNG、3 张效果图 PNG。
- Debug APK：`app/build/outputs/apk/debug/app-debug.apk`，约 110 MB（含既有模型与依赖）。
