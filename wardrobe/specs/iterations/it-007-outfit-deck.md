# it-007 · 穿搭记录卡组化（快速浏览 + 随机一套）

- **状态**：已完成
- **提案日期**：2026-09-20
- **范围**：W8 穿搭记录页顶部新增侧滑卡组（复用 `libs/carddeck`），下方保留全量网格

## 背景与动机

与 eats it-003 同批：已保存的穿搭希望能**侧滑卡片快速预览**、也能**随机抽一套**（不知道穿什么时）。
交互沉淀在 `libs/carddeck` SDK，两应用复用（AGENTS.md libs 规范）。

## 变更

- RecordsScreen：顶部「随机一套」按钮 + CardDeck（穿搭大卡：拼贴图 + 标签，点击进详情），
  下方保留「全部 N 套」网格总览；标签筛选对卡组与网格同时生效
- 演示数据脚本补 5 套已保存穿搭（17 件衣物组合）
- 接线：`includeBuild("../libs/carddeck")` + `com.leo.libs:carddeck:0.1.0`（settings 同步加 JitPack 仓库）

## 验证记录

- assembleDebug ✅；模拟器实测 `assets-it-006/wardrobe-05-records-deck.png`（卡组+网格无错乱）、
  `wardrobe-06-records-drawn.png`（随机一套落定），视觉模型验收通过

## 遗留

- 卡组抽取落定后可加「就穿这套」高亮或直达详情的强化反馈（当前顶部卡即结果）
