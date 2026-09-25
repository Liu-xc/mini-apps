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
            "（演示模式）这是离线假模型的回复。我刚用 search_items 查了你的衣橱——" +
                "工具与会话链路都是真的，只是模型回复为固定文案。退出演示模式并在设置里配置 API Key 后，" +
                "这里会是真实模型基于你衣橱数据的回答。",
        ),
    ),
)
