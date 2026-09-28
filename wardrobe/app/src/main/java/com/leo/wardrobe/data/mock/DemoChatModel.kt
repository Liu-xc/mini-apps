package com.leo.wardrobe.data.mock

import com.leo.libs.agent.ToolCall
import com.leo.libs.agent.testing.FakeChatModel
import com.leo.libs.agent.testing.fakeText
import com.leo.libs.agent.testing.fakeToolCalls

/**
 * it-041 演示模式离线模型：每次对话新造一发脚本——第一步「调用」search_items
 * （工具 handler 走真实只读查询，演示数据也被真查），第二步给固定文案。
 * 走查零外呼（红线②配套）；真实模式下不存在此分支。
 */
fun demoChatModel(): FakeChatModel = FakeChatModel(
    listOf(
        fakeToolCalls(ToolCall("demo-1", "search_items", "{}")),
        fakeText(
            "## 第一套 · 通勤\n\n" +
                "**适合**：办公室 / 早秋\n\n" +
                "- 上装：牛津纺衬衫\n" +
                "- 外套：羽绒服\n" +
                "- 下装：直筒牛仔裤\n" +
                "- 鞋：板鞋\n" +
                "- 包：手提包\n\n" +
                "**理由**：衬衫和直筒牛仔裤是干净利落的通勤底子，外套应对早晚温差，板鞋和手提包让整体保持轻松但不随意。\n\n" +
                "> 这是演示模式：卡片里的图片与名称来自当前演示衣橱。退出演示模式并配置 API Key 后，" +
                "顾问会根据你的真实衣橱生成同样结构的回复。",
        ),
    ),
)
