# it-076 · 会话列表长按删除（W12）

> 状态：**提案待拍板**（2026-09-30，Leo 需求：「对话列表支持删除」）

## 背景与动机

W12 会话列表只能新增和进入，没有删除入口——演示/日常产生的会话会无限堆积，误开的空会话也清不掉。消息文件（`agent-sessions/<id>.json`）与索引（`index.json`）都只增不减。

## 方案

交互对齐 W3 衣物卡先例（it-037 定语：网格/列表里滑动会让位滚动，统一**长按删除**）：

- **SessionCard 长按** → AlertDialog 确认（W3 同款形制：「删除「{标题}」？」+「对话消息将一并删除，无法恢复。」+ 取消/删除）→ 确认后删除。
- **ChatSessionIndex.remove(sessionId)**：mutex 下从索引移除并原子写回（与 create/refresh 同形制）。
- **ChatViewModel.deleteSession(id)**：`session.clear(id)`（FileSessionStore 已有，删消息文件）+ `chatSessions.remove(id)` + `refreshSessions()`；若删的是当前打开的会话则同时清空 `sessionId` 防残留。
- **只读态（未配 Key）同样可删**：删除是纯本地操作不涉 API，历史堆积同样需要清理。
- 删空后列表回落现有空态（现逻辑天然支持）。

## 用户故事（US-63，W12）

作为用户，我想删除不再需要的对话，保持顾问列表干净。
- Given W12 会话卡长按，Then 弹确认对话框（标题 + 后果说明 + 取消/删除）
- Given 确认删除，Then 会话卡即时消失，`agent-sessions/<id>.json` 与索引条目一并删除；杀进程后不复现
- Given 删除全部会话，Then 列表回落空态（按 canChat 显示对应引导）
- Given 未配置 Key 的只读态，Then 仍可长按删除历史会话
- Given 删除不存在的会话 id，Then 不崩、索引不变

## 验收标准

1. 长按 → 确认 → 删除 → 列表即时更新；取消则不动。
2. 删除后重进 app 会话不复现（消息文件与索引双删）。
3. 只读态可删；删空回落空态。
4. `ChatSessionIndexTest` 增 remove 用例（正常删/删空/不存在不崩）全绿，全量回归绿。
5. 模拟器实测截图回填。

## 影响范围

`ChatSessionIndex.kt`（+remove）、`ChatViewModel.kt`（+deleteSession）、`ChatListScreen.kt`（长按 + 对话框）；spec：01（US-63）、02（W12 一行）、CHANGELOG。不动 SDK、不动数据包。

## 待拍板

交互形态默认**长按 + 确认对话框**（与 W3 同语言）；若想要右滑删除（列表竖向滚动与横滑不冲突）回复改即可，其余无拍板点。

## 实施记录

（待拍板后回填）

## 验证记录

（待实施后回填）
