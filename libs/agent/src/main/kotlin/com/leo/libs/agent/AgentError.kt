package com.leo.libs.agent

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Agent 链路错误分类；[userMessage] 面向用户，UI 据此给可读文案与重试按钮 */
sealed class AgentError(message: String, cause: Throwable? = null) : Exception(message, cause) {

    /** key 无效/未授权（401/403），或本地未配置 key */
    class Auth(detail: String = "未配置 API Key 或 Key 无效") : AgentError(detail)

    /** 额度不足/欠费（402，或错误文案命中余额关键词） */
    class Quota(detail: String = "额度不足或已欠费") : AgentError(detail)

    /** 限流（429）；[retryAfterSeconds] 取自 Retry-After 头，可能为 null */
    class RateLimit(val retryAfterSeconds: Long?, detail: String = "请求过于频繁") : AgentError(detail)

    /** 网络不可达（含代理问题） */
    class Network(cause: Throwable) : AgentError("网络不可达：${cause.message}", cause)

    /** 厂商返回协议外错误（5xx / 空 choices / 流中断无内容等） */
    class Provider(val httpCode: Int, detail: String) : AgentError(detail)

    /** 请求本身不合法（400：模型名 / 工具 schema / 参数错误） */
    class Schema(detail: String) : AgentError(detail)

    val userMessage: String get() = message ?: "未知错误"

    companion object {
        private val quotaKeywords = listOf("余额", "欠费", "balance", "insufficient")

        /** HTTP 非 2xx → 错误分类；body 尽力解析 {error:{code,message}} 或 {message}，失败则截取原文 */
        fun fromHttp(httpCode: Int, body: String, retryAfter: String? = null): AgentError {
            val detail = detailOf(httpCode, body)
            return when {
                httpCode == 401 || httpCode == 403 -> Auth(detail)
                httpCode == 402 -> Quota(detail)
                httpCode == 429 -> RateLimit(retryAfter?.trim()?.toLongOrNull(), detail)
                httpCode == 400 && quotaKeywords.any { detail.lowercase().contains(it) } -> Quota(detail)
                httpCode == 400 -> Schema(detail)
                else -> Provider(httpCode, detail)
            }
        }

        private fun detailOf(httpCode: Int, body: String): String {
            val parsed = runCatching {
                val obj = Json.parseToJsonElement(body).jsonObject
                val err = obj["error"]?.jsonObject
                val code = err?.get("code")?.let { (it as? JsonPrimitive)?.content ?: it.toString() }
                val msg = err?.get("message")?.jsonPrimitive?.content
                    ?: obj["message"]?.jsonPrimitive?.content
                listOfNotNull(code?.let { "[$it]" }, msg).joinToString(" ")
            }.getOrNull().orEmpty()
            return parsed.ifBlank { body.take(200).ifBlank { "HTTP $httpCode" } }
        }
    }
}
