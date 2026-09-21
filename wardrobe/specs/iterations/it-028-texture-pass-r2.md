# it-028 · 质感 pass R2：触控目标 + 首屏入场落地 + 触感补线

- **状态**：实施中（2026-09-21 Leo 指示「持续优化」，DESIGN.md 对表第二遍）
- **对表基准**：[DESIGN.md](../../../DESIGN.md) §2.5 / §3 / §4

## 审计发现与修复

| # | 发现（条款） | 修复 |
|---|---|---|
| 1 | 评论删除钮命中区 28dp（§2.5 ≥44） | IconButton 28→44dp，视觉图标 14dp 不变 |
| 2 | 卡片「···」菜单钮命中区 28dp（§2.5，媒体角标例外） | 28→36dp（新例外档，见 DESIGN.md §2.5 增补） |
| 3 | 列表入场动画缺失但 spec 05 #7 声明存在（spec-code 不一致） | 新增 `StaggeredEntrance` 组件落地 W3/W8 两网格：24ms 错峰 fade+上移，`rememberSaveable` 标志**仅首进播放**（§3 预算），与 eats 对齐 |
| 4 | 导出面板「已复制 ✓」常驻不复位（存相册 2s 复位，不一致） | 加同拍 LaunchedEffect 2s 复位 |
| 5 | 触感漏线（§4 保存类） | 角色编辑/新建保存、心愿「收进想买/保存」、心愿转正、心愿穿搭升级 = confirm ×5 |

## 验收标准

- Given 衣橱/穿搭记录首进，Then 网格条目 24ms 错峰淡入上移；Given 从详情返回，Then 不重放
- Given 评论删除/卡片菜单钮，Then 命中区 ≥44dp / ≥36dp（媒体角标），视觉不变
- Given 五处保存类动作成功，Then 一次确认震感
- 构建与单测全绿；模拟器冒烟无崩溃

## 验证记录（2026-09-21 回填）

- 构建 + 单测：`./gradlew :app:assembleDebug :app:testDebugUnitTest` 全绿——**62 tests, 0 failures**（首轮编译错 2 处已修：PersonSheet decl 位置、Inputs→wardrobe 无涉）。
- 模拟器冒烟（emulator-5554）：安装启动正常；衣橱网格经 `StaggeredEntrance` 包裹后渲染正确（18 件卡片、「···」角标正常）；logcat 零 FATAL。
- 入场预算：两屏 `entranceDone`（rememberSaveable）900ms 后置位，返回不重放（代码层验证）。
- 触感新增 5 处为物理反馈，待真机手感复核。
