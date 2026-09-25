package com.leo.libs.agent.session

import com.leo.libs.agent.Message
import com.leo.libs.agent.Role

/**
 * 上下文裁剪（it-002 §3）：发给模型前对历史做窗口控制。
 * system 常驻由 [com.leo.libs.agent.AgentRunner] 负责（prepend），本接口只处理 history。
 */
interface ContextPolicy {
    fun trim(history: List<Message>, system: Message?): List<Message>
}

/**
 * 默认策略（架构 §7）：近 [maxTurns] 轮 + 字符/4 估算 token 超 [maxEstimatedTokens] 裁最老。
 * - 裁剪只发生在「user 消息边界」——assistant 与它的 tool 结果同进同出，不产生孤儿 tool_call；
 * - 开头的 system / 游离 assistant、tool（破损历史）直接丢弃；
 * - 单轮再大也不裁掉最后一个 user 边界（至少保住当前问题）。
 */
class DefaultContextPolicy(
    private val maxTurns: Int = 12,
    private val maxEstimatedTokens: Int = 8_000,
) : ContextPolicy {

    override fun trim(history: List<Message>, system: Message?): List<Message> {
        var list = history.filter { it.role == Role.User || it.role == Role.Assistant || it.role == Role.Tool }
        list = list.dropWhile { it.role != Role.User }
        if (list.isEmpty()) return list

        // 近 N 轮：保留最后 maxTurns 个 user 起始边界之后的全部
        val userStarts = list.indices.filter { list[it].role == Role.User }
        if (userStarts.size > maxTurns) {
            list = list.subList(userStarts[userStarts.size - maxTurns], list.size)
        }

        // token 估算超窗：从最老的 user 边界开始整轮裁
        while (list.size > 1 && estimate(list) > maxEstimatedTokens) {
            val nextUser = (1 until list.size).firstOrNull { list[it].role == Role.User } ?: break
            list = list.subList(nextUser, list.size)
        }
        return list
    }

    private fun estimate(messages: List<Message>): Int {
        var chars = 0
        for (m in messages) {
            chars += m.text.length
            m.toolCalls.forEach { chars += it.argumentsJson.length }
        }
        return chars / 4
    }
}
