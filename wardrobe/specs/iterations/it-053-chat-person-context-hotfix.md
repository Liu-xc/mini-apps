# it-053 · 顾问角色上下文回退修复

- **日期**：2026-09-28
- **状态**：实现与本地验证完成，待手机验收
- **类型**：纯 bugfix
- **来源**：Mock 包对话截图；衣橱工具连续返回“当前没有角色”

## 问题与根因

W1/W2 的 `AppViewModel.currentPerson` 在角色偏好为空或 id 已失效时会回退到衣橱中的首个角色；W13 `ChatViewModel` 却直接读取 `PrefsStore.currentPersonId`，因此新装 Mock 虽有内置角色，工具仍收到 `null`，模型随后重复调用工具并把“当前没有角色”当成衣橱数据问题。

截图中的助手已返回工具调用与最终回复，说明请求到达了模型处理阶段；故障在角色上下文。另按 MiMo Token Plan 官方快速验证方法执行了一次编程请求自检（`mimo-v2.6-flash`、Kotlin ping、HTTP 200）；凭证没有写入仓库或 APK，也没有用于衣橱推荐请求。

## 修复

1. 在 `WardrobeData.currentPersonOrFirst(savedId)` 中集中定义「有效保存角色优先，否则首个角色」规则。
2. W1/W2 与 W13 共同使用该规则；有效的显式角色选择仍然优先。
3. 衣橱确实没有角色时，在本地返回选择/创建指引，不为该请求外呼；系统提示约束模型不重复发送相同条件查询。

## 验收标准

- 已保存的有效角色优先被顾问工具使用。
- 偏好为空或保存 id 已失效时，顾问使用与主 UI 一致的首个角色，并查询该角色的真实衣橱数据。
- 衣橱确实没有角色时，应用本地给出一次清晰的选择/创建提示，不发起模型请求。
- MiMo Token Plan Key 不写入代码、规格、APK 或日志。

## 影响范围

- **代码**：`domain/model/Queries.kt`、`ui/AppViewModel.kt`、`ui/chat/ChatViewModel.kt`
- **常青 spec**：US-41b、W13 交互、角色解析架构说明。
- 不涉及数据结构、持久化格式或依赖变更。

## 验证记录

- `./gradlew -PdemoDefault=true :app:testDebugUnitTest :app:assembleDebug`：BUILD SUCCESSFUL；71 项 JVM 单测通过，0 失败。
- `BuildConfig.DEMO_DEFAULT = true`；已更新 Mock APK 和同地址下载包，HTTP 下载返回 200。
- API 凭证自检：通过 Token Plan China endpoint，使用应用当前的 Bearer 认证方式和 `mimo-v2.6-flash` 执行一次 Kotlin ping；HTTP 200，返回 Kotlin 代码。未使用该凭证发起穿搭推荐请求。
- 未在模拟器覆盖用户正在使用的页面，本包交由手机验收。
