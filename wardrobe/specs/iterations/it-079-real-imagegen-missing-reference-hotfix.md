# it-079 — 修复真实生图请求参数

## 背景与问题

退出演示模式后用已配置的 SiliconFlow `Qwen/Qwen-Image-Edit` 实际调用，服务端先返回 `Missing required key: image`。修复参考长图解析后，该错误消失，但服务端进一步返回 `seed should be a integer`。

根因有两处：

1. 参考图读取：`OutfitImageComposer.composeToExportFile` 返回 `files/export/` 下的绝对路径；`fileToDataUri` 把它再次传入 `ImageStore.file`，后者会按 `files/images/<name>` 解析。Android `File(parent, absoluteChild)` 会错误拼到 `images/` 下。解码因此返回空，`buildRefs` 静默生成空列表，HTTP 请求体漏掉 `image`。
2. 生图高级参数：UI 把值收集为字符串，`OutfitImageGenerator.run` 又把所有值都封成 JSON string。Qwen 的 `seed` 声明为整数，服务端拒绝字符串类型。

## 用户故事

- **US-64a（W7/W11，直达生成）**：选择 Qwen 图文编辑模型时，参考长图随请求上传，真实模型结果显示为候选。
- **US-64b（W13，对话流）**：共用的生图生成器正确读取合成参考图并传给真实 provider。

## 验收标准

1. 绝对路径的合成参考长图直接从该路径读取，不再经过 `ImageStore` 的图片目录解析。
2. 普通相对图片文件名仍由 `ImageStore` 解析。
3. 高级参数按 `ImageParamSpec` 声明保留布尔、整数、浮点或字符串类型。
4. 提示词明确说明单张长图是文字与单品拼贴，禁止原样复刻参考长图；没有人物照片时要求生成真人模特。
5. W7 真实 SiliconFlow/Qwen 请求包含单张 data URI 参考图和数值 seed，返回真人穿搭图候选；候选不是内置 mock 样张。

## 影响范围

- `OutfitImageGenerator`：绝对路径/媒体文件名分别解析；参数按模型声明编码 JSON 类型。
- `BuildTryOnPrompt`：提示词与单张合成长图的结构一致，禁止复制长图拼贴。
- 新增纯 JVM 测试覆盖路径解析、高级参数类型与提示词约束。

## 验证记录

- 初次 W7 真实调用：复现服务端 `Missing required key: image`。
- 根因通过 Android `File(parent, absoluteChild)` 行为和当前合成长图输出路径确认；单元测试覆盖修正后的路径分支。
- 第二次请求曾进一步复现 `seed should be a integer`；修复按 `ImageParamSpec` 输出数字/布尔 JSON 值。
- `./gradlew :app:testDebugUnitTest assembleDebug`：通过；调试 APK 安装到 `wardrobe_test` 模拟器。
- 模拟器确认 `demo_enabled=false`，设置为 SiliconFlow `Qwen/Qwen-Image-Edit`。W7 点击生成后可见「生成中…（已等待 3s）」进度状态；约 43 秒后返回候选，页面显示模型名 `Qwen/Qwen-Image-Edit`。
- 检查实际候选截图：返回真人全身穿搭图，不是内置 mock 样张、蓝色占位图或原样拼贴；单品遵循度仍有瑕疵（额外出现侧置牛仔裙，包与运动鞋未清晰呈现），因此验证确认真实请求/结果展示链路打通，不代表生成质量完全达标。未点「保存到这套穿搭」，避免改动衣橱数据。
- 实测截图：`/tmp/wardrobe-it079-result.png`；生成过程截图：`/tmp/wardrobe-it079-running.png`。
