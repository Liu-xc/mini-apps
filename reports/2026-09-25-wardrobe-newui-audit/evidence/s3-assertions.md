# S3 交互实测证据（2026-09-25，emulator-5554，最新构建 9b63b48）

| # | 断言 | 手段 | 结果 |
|---|---|---|---|
| E1 | W11 设置页可从 W3 ⚙ 进入、白顶栏沉浸 | uiautomator dump（evidence/dump-w11-top.xml） | ✓ 模型连接卡/厂商/模型/API Key/按钮齐全 |
| E2 | 用量卡显示厂商×模型与 tokens | dump（dump-w11-bottom.xml） | ✓「智谱 GLM · glm-4-flash」「6016 tokens」（对话后从 3508→6016 实时更新） |
| E3 | Key 只显 mask | dump | ✓「已保存 1b0d***EW0f · 留空保持不变」；明文 key 全仓 grep 零命中 |
| E4 | 自检成功呈现 | 操作后 dump | ✓「连通正常 · glm-4-flash」（真调 GLM） |
| E5 | 自检失败分类文案 | 用被拒 key 触发 | ✓「[401] 令牌已过期或验证不正确」（Auth 分类透传，见截图 07） |
| E6 | W12 空态 | 清会话后 dump（dump-w12-empty.xml） | ✓「问问你的衣橱」+ 示例 hint + EmptyState |
| E7 | 发送→工具→回复全链路 | dump（dump-w12-reply.xml）+ 会话文件核对 | ✓「已查衣橱：search_items」+ 真实单品名回复；会话文件 11 条消息含 4 轮工具 |
| E8 | 杀进程会话持久 | am force-stop → 重开 dump | ✓ 历史完整恢复（US-41c） |
| E9 | 错误横幅+重试 | 清 Key 后发送 dump（dump-w12-error.xml） | ✓ 分类红字 + 重试/知道了 |
| E10 | W5 状态条四态 | 逐态 dump + 像素指纹 | ✓ 原图按钮/候选棋盘格(46,268px)/保留后已抠态/重新抠图各态截图 08-10 |
| E11 | alpha 检测正确性 | 36 张 mock 单品图 ground truth | ✓ 全部 frac=0.000→判原图，与 UI 呈现一致（无误判） |
| E12 | 演示模式素材解包 | 删 item-11 重启 | ✓ 缺才拷自愈；期间发现 item-12 曾缺失（见备注） |
| E13 | 走查中发现的自动化坑（非 app 缺陷） | — | 保存按钮 y 随状态行出现而位移(1244↔1332)；input text 输 key 曾致中段损坏→401，重灌后自检 ✓ |

**备注（环境）**：走查开始时演示缓存缺 item-12.png（APK 内有），奶油色针织开衫会显占位；重启后自愈——建议走查前清 `cache/mock-images` 重解包。

## 源码事实（供视觉结论复核）
- 状态条四态与棋盘格：`ui/detail/ItemDetailScreen.kt`（it-040 块）、`ui/wardrobe/ItemEditScreen.kt`（W4 同构）
- 用量 count-up：`ui/settings/SettingsScreen.kt` 用量卡 + `ui/components/CountUpText`
- 错误分类：`libs/agent AgentError.userMessage`；横幅=「重试/知道了」TextButton 对
- token 红线：ink/paper≥7:1、白/accent≥4.5:1（it-038 token）；触控 ≥44/48dp（it-033）
