# it-083 生图 prompt 按参考图分支 + 禁止衣架陈列约束

- 状态：已实施
- 日期：2026-10-03
- 类型：bugfix（生图画风跑偏）
- 关联：it-077（prompt 模板与参考长图）、it-082（真机生图 400 修复后链路已通）

## 问题

Leo 真机（it-082 后）生成成功但出图是「衣物挂衣架/人台陈列」，无真人模特。根因两层：

1. **prompt 不按「是否带参考图」分支**：纯文生图模型（Kolors/Z-Image-Turbo，inputImages=0..0）的参考长图在 `buildRefs` 已被丢弃，但模板仍说「根据输入的穿搭长图……拼贴中的单品照片」——模型收到「看图」指令却无图，转而按字面把「拼贴/陈列」画出来。
2. **缺负面约束**：无「不要衣架/人台/平铺」表述，参考长图本身是衣架/平铺素材（图生图路径同样被带偏）。

## 修法

`BuildTryOnPrompt.build` 增第 4 参 `hasReferenceImage`（默认 true 兼容旧调用）：

- **true（图生图）**：保留长图说明，新增「陈列方式只是参考素材，必须把衣架/人台上的衣物穿到真人身上」
- **false（纯文生图）**：全新文案——只凭衣物清单生成真人试衣实拍，全文不出现「长图/拼贴」
- **两分支共同**：末尾加「负面约束：画面中不得出现衣架、人台、假人模特、平铺陈列或橱窗展示，所有衣物必须穿在真人身上」（内联写法——Z-Image-Turbo 无 negative_prompt 字段，内联对所有模型通用）

接线：`OutfitImageGenerator.promptOf` 透传参数；生成 sheet（键含 `modelTakesImages`，就地换模型跨 0..0↔1..1 档时重置模板）与顾问对话工具两调用点均按 `conn.modelSpec.inputImages.first > 0` 传值。

## 二次修订（同日晚：全身构图）

Leo 真机复审：出图已是真人模特但非全身。两层修复：

1. **prompt 强化**：两分支开头即声明「从头到脚完整入镜，头部与所穿鞋子都必须出现在画面中」；构图行改「全身照，从头顶到脚底完整入镜，必须包含所穿鞋子，不得裁切头部、腿部或脚部，不得拍成半身或七分身」
2. **默认画布竖幅**（`ModelCatalog`）：Kolors size 默认 `1024x1024`→`960x1280`、Z-Image-Turbo→`864x1152`——正方形画布是全身被裁半身的另一主因；UI 面板仍可换回其他档

并行会话工作区半成品（WishlistScreen 等）导致主树编译不过，按预案 `git worktree add HEAD` 干净构建装机；测试在 worktree 内全绿（含新增「全身构图约束两分支齐备」断言）。提交 `c4c5bff`。

## 验证

- [x] `BuildTryOnPromptTest` +2：纯文生图分支断言不含「长图/拼贴」且含真人+衣架负面词；参考图分支断言「穿到真人身上」「不得出现衣架、人台…」；旧 3 case 全兼容
- [x] `./gradlew test` 全绿（worktree 干净 HEAD）
- [x] it083 包装机（v0.5.0.141-dirty）；二次修订包 `wardrobe-it083-full-1003.apk`（c4c5bff，worktree 干净构建）
- [ ] Leo 真机重生成出全身真人穿搭图（待反馈）

## 影响

- `domain/usecase/BuildTryOnPrompt.kt`、`data/gen/OutfitImageGenerator.promptOf`、`ui/records/OutfitGenerateSheet.kt`、`ui/chat/ChatViewModel.kt`、对应测试
