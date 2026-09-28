package com.leo.wardrobe.data.chat

import com.leo.libs.agent.Message
import com.leo.libs.agent.session.FileSessionStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class ChatSessionIndexTest {
    @Test
    fun `新会话独立登记并按最近更新时间倒序`() = runBlocking {
        val dir = Files.createTempDirectory("chat-session-index").toFile()
        try {
            val store = FileSessionStore(dir)
            val index = ChatSessionIndex(dir.resolve("index.json"), store)
            val first = index.create()
            store.append(first.id, Message.user("通勤怎么穿").copy(createdAt = 100L))
            index.refresh(first.id, store.messages(first.id))
            val second = index.create()
            store.append(second.id, Message.user("下雨天搭配").copy(createdAt = 200L))
            index.refresh(second.id, store.messages(second.id))

            val listed = index.list()
            assertEquals(listOf(second.id, first.id), listed.map { it.id })
            assertEquals("通勤怎么穿", listed.last().title)
            assertEquals("下雨天搭配", listed.first().preview)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `旧单会话首次读取登记为历史记录且消息保持不变`() = runBlocking {
        val dir = Files.createTempDirectory("chat-session-legacy").toFile()
        try {
            val store = FileSessionStore(dir)
            store.append("wardrobe-chat", Message.user("历史问题").copy(createdAt = 100L))
            store.append("wardrobe-chat", Message.assistant("历史回答").copy(createdAt = 200L))
            val index = ChatSessionIndex(dir.resolve("index.json"), store)

            val legacy = index.list().single()
            assertEquals("wardrobe-chat", legacy.id)
            assertEquals("历史问题", legacy.title)
            assertEquals("历史回答", legacy.preview)
            assertEquals(2, store.messages("wardrobe-chat").size)
            assertTrue(dir.resolve("wardrobe-chat.json").exists())
        } finally {
            dir.deleteRecursively()
        }
    }
}
