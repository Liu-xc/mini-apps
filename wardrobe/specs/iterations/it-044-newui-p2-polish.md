# it-044 · 新三面打磨（P2 批）

- **状态**：**已实现并验收（2026-09-25，Leo 指令「实施优化建议」）**——构建+单测绿、走查/源码双证，见文末验证记录
- **来源**：[2026-09-25 新三面走查报告](../../../reports/2026-09-25-wardrobe-newui-audit/) 落地拆分 O5–O7
- **关联**：US-44（新增）；05-design-system 增补

## 背景与动机

P1 批（it-043）修体验骨架，本迭代收 P2 打磨项：对齐体系、会话后续动作、设计说明沉淀。

## 用户故事

### US-44 新三面 P2 打磨（W11/W12/W5）
作为用户，新页面的对齐、间距与后续动作经得起细看，设计决策有 spec 可查。
- **UC**：W11；W12；W5
- Given W11 表单行，Then 下拉内容右缘与输入框右缘同一对齐线（O5）
- Given W5/W12 标签 chips，Then 间距统一（TagRow 全站 10dp，O5）
- Given 一条完整回复，Then 气泡下有「复制 / 追问」动作（复制走剪贴板并 toast，追问一键发送）（O6，报告 C11）
- Given IME 回车，Then 与应用内发送同一行为（已为 send，验证记录佐证；键盘键色为系统行为不在应用侧）
- Given 字体声部、用量「累计」口径、候选态棋盘语义，Then 写入 05-design-system（O7）

## 验收标准

1. 构建与单测绿；走查验证对齐与气泡动作行。
2. 复制后有 toast 反馈；追问 chip 发送成功。
3. 05-design-system 增补 it-044 节；01（US-44）、02 注记、CHANGELOG、验证记录回填。
4. W5 图区内边距结论如实记录：外框三态统一 20dp，mat 衬纸与棋盘格为语义差异不强行统一（设计决定入 05）。

## 影响范围

- `ui/settings/SettingsScreen.kt`（对齐）、`ui/components/Tags.kt`（间距）、`ui/chat/ChatScreen.kt` + `ChatViewModel.kt`（动作行）、
  `ui/detail/ItemDetailScreen.kt`（棋盘语义 hint）、`specs/05-design-system.md`

## 验证记录 · 2026-09-25

- **构建/测试**：`./gradlew assembleDebug testDebugUnitTest` BUILD SUCCESSFUL。
- **O5**：W11 模型下拉可点区右缘 x=974 = API Key 输入框右缘 x=974（uiautomator bounds 实测**同线✓**，原差 63px）；TagRow/TagInput 三处行距 6→10dp（代码级）。
- **O6**：回复气泡下「复制 / 换个场合再推荐」动作 chip 在位✓；点复制 → 全局 toast「已复制回复」✓（截图 w12-reply-collapsed-actions.png）；追问 chip 为 `vm.send` 同发送通道（与示例 chip 同路径，示例 chip 发送已实测✓）；IME 行为 = `KeyboardActions(onSend)` 直发（代码级，与应用内发送同一 `sendCurrent()`）——键盘回车键色为系统 IME 行为，不在应用侧（如实记录）。
- **O7**：候选态注记「棋盘格 = 透明底」实测在位✓（w5 走查）；`05-design-system.md` 新增「it-043～044 新三面审美收口」节（主 CTA 实心/指引不降级/状态条容器/字体声部定案/累计口径/chips 10dp/右缘对齐/图区内边距设计决定 8 条）。
- **演进候选（如实记录）**：回复「重新生成」需 SessionStore 尾删能力（SDK 现仅 append/messages/clear），记为候选不入本迭代——US-44 已注明。
