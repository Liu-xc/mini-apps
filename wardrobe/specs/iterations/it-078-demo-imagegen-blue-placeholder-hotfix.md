# it-078 — 修复演示生图蓝色占位图

## 背景与问题

W7「生成效果图」在演示模式下会直接显示纯蓝色图片，生成中的 loading 很难被观察到，也看不到穿搭效果图。排查确认：`MockImageModel` 没有参考图时回退到内嵌的 1×1 PNG。该 PNG 能被 Coil 正常解码，像素实际为半透明蓝色 `(0, 0, 255, 127)`，因此被拉伸成候选图，而非触发加载或解码失败 UI。假模型同时在读取结果后立即完成，演示生图态几乎不可见。

此问题影响 W7/W13 共用的演示生图假实现；不改变真实 provider 的请求与响应处理。

## 用户故事

- **US-64a（W7/W11，直达生成）**：演示模式也应展示有效的生图流程与候选结果，且候选能保存到穿搭。
- **US-64b（W13，对话流）**：共用演示生图假实现返回有效效果图，避免对话生图收到蓝色占位图。

## 验收标准

1. 演示模式无论参考图是否为空，都以包内 `effect-weekend.png` 作为候选，不再出现蓝色 1×1 占位图。
2. 演示模式显示「演示生成中…」至少 700ms，再显示效果图候选。
3. 样张缺失或读取失败时进入明确失败态，不生成空白/占位候选。
4. 候选可保存并显示在穿搭成品图中。

## 影响范围

- `MockImageModel`：移除蓝色 PNG fallback，读取演示样张；为演示生成态保留可见时长；样张读取失败时发出 `Failed` 事件。
- `OutfitImageGenerator`：演示模式将 `effect-weekend.png` 注入假模型；真实 provider 路径不变。
- `AppContainer`：更新 `MOCK_ASSET_REVISION`，刷新设备上的演示资源缓存。
- 新增 `MockImageModelTest` 覆盖无参考图时的候选和样张缺失失败态。

## 验证记录

- `cd wardrobe && ./gradlew :app:testDebugUnitTest assembleDebug`：通过。
- `cd libs/agent && ./gradlew test`：通过（UP-TO-DATE；此迭代未改 SDK）。
- Android 模拟器 `emulator-5554`：安装当前 debug APK，在隔离演示模式完成 W7 生图候选与保存到穿搭闭环；候选及 W8 已保存卡均显示包内真实穿搭样张，无蓝色块。
- 曾尝试在该模拟器用真实 provider 生图，返回 `Missing required key: image`。同机 TUN 代理对大型 base64 请求体的干扰已在 it-077 记录；该错误不影响此次演示模式回归验证。真实网络环境仍需在设备网络正常时复测。
