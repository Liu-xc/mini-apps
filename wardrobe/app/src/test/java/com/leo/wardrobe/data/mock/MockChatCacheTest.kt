package com.leo.wardrobe.data.mock

import com.leo.libs.agent.ChatEvent
import com.leo.libs.agent.ChatRequest
import com.leo.libs.agent.Usage
import com.leo.libs.agent.Message
import com.leo.libs.agent.testing.FakeChatModel
import com.leo.libs.agent.testing.fakeFailing
import com.leo.libs.agent.testing.fakeText
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class MockChatCacheTest {
    @Test
    fun `相同 Mock 请求第二次命中缓存且不再消费模型脚本`() = runBlocking {
        val dir = Files.createTempDirectory("mock-chat-cache-test").toFile()
        try {
            val fake = FakeChatModel(listOf(fakeText("缓存答案")))
            val cache = MockChatCache(dir)
            val model = CachedMockChatModel(fake, cache) { "test-key-a" }
            val request = ChatRequest(messages = listOf(Message.user("配一套通勤装")))

            model.stream(request).toList()
            assertEquals(0, dir.listFiles().orEmpty().size) // 先暂存，整轮完成前不能落缓存
            model.commitPending()
            val cached = model.stream(request).toList()

            assertEquals(1, fake.requests.size)
            assertTrue(model.hadCacheHit)
            assertEquals("缓存答案", (cached.last() as ChatEvent.Completed).completion.message.text)
            assertEquals(Usage(), (cached.last() as ChatEvent.Completed).completion.usage)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `连通性自检 complete 始终调用模型且不写聊天缓存`() = runBlocking {
        val dir = Files.createTempDirectory("mock-chat-cache-ping-test").toFile()
        try {
            val fake = FakeChatModel(listOf(fakeText("pong"), fakeText("pong")))
            val cache = MockChatCache(dir)
            val model = CachedMockChatModel(fake, cache) { "test-key" }
            val ping = ChatRequest(messages = listOf(Message.user("ping")), maxTokens = 8)

            model.complete(ping)
            model.complete(ping)

            assertEquals(2, fake.requests.size)
            assertEquals(0, dir.listFiles().orEmpty().size)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `测试 Key 指纹变化不会复用缓存且失败不写入`() = runBlocking {
        val dir = Files.createTempDirectory("mock-chat-cache-key-test").toFile()
        try {
            val cache = MockChatCache(dir)
            val request = ChatRequest(messages = listOf(Message.user("我的外套")))
            val first = CachedMockChatModel(FakeChatModel(listOf(fakeText("A"))), cache) { "key-a" }
            first.stream(request).toList()
            first.commitPending()
            val secondFake = FakeChatModel(listOf(fakeText("B")))
            val second = CachedMockChatModel(secondFake, cache) { "key-b" }
            second.stream(request).toList()
            second.commitPending()
            assertEquals(1, secondFake.requests.size)
            assertFalse(second.hadCacheHit)

            val failing = CachedMockChatModel(
                FakeChatModel(listOf(fakeFailing(com.leo.libs.agent.AgentError.Network(RuntimeException("offline"))))),
                cache,
            ) { "key-fail" }
            runCatching { failing.stream(request).toList() }
            assertTrue(dir.listFiles { file -> file.extension == "json" }.orEmpty().size == 2)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `整轮取消或失败时丢弃已完成步骤的暂存`() = runBlocking {
        val dir = Files.createTempDirectory("mock-chat-cache-abort-test").toFile()
        try {
            val cache = MockChatCache(dir)
            val request = ChatRequest(messages = listOf(Message.user("测试问题")))
            val fake = FakeChatModel(listOf(fakeText("第一步"), fakeText("重试答案")))
            val model = CachedMockChatModel(fake, cache) { "test-key" }

            model.stream(request).toList()
            model.discardPending()
            model.stream(request).toList()
            model.commitPending()

            assertEquals(2, fake.requests.size)
            assertEquals(1, dir.listFiles { file -> file.extension == "json" }.orEmpty().size)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `流式请求在终态前取消不会写缓存`() = runBlocking {
        val dir = Files.createTempDirectory("mock-chat-cache-cancel-test").toFile()
        try {
            val cache = MockChatCache(dir)
            val model = CachedMockChatModel(FakeChatModel(listOf(fakeText("这是一个未完成的回答"))), cache) { "test-key" }
            val request = ChatRequest(messages = listOf(Message.user("问题")))

            model.stream(request).take(1).toList()
            model.commitPending()

            assertEquals(0, dir.listFiles().orEmpty().size)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `清除操作删除 Mock 顾问缓存`() = runBlocking {
        val dir = Files.createTempDirectory("mock-chat-cache-clear-test").toFile()
        try {
            val cache = MockChatCache(dir)
            val model = CachedMockChatModel(FakeChatModel(listOf(fakeText("可清理"))), cache) { "test-key" }
            val request = ChatRequest(messages = listOf(Message.user("清除测试")))

            model.stream(request).toList()
            model.commitPending()
            assertEquals(1, dir.listFiles { file -> file.extension == "json" }.orEmpty().size)

            cache.clear()

            assertEquals(0, dir.listFiles { file -> file.extension == "json" }.orEmpty().size)
            assertFalse(model.hadCacheHit)
        } finally {
            dir.deleteRecursively()
        }
    }
}
