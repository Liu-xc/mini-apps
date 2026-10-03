package com.leo.libs.agent

import com.leo.libs.agent.image.DashScopeTaskImageModel
import com.leo.libs.agent.image.ImageModel
import com.leo.libs.agent.image.OkHttpImageModel
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** it-077：统一模型目录数据完整性 */
class ModelCatalogTest {

    @Test
    fun `厂商 id 唯一`() {
        val ids = ModelCatalog.all.map { it.id }
        assertEquals(ids.size, ids.distinct().size)
    }

    @Test
    fun `glm 聊天档位从 preset 派生且均可工具调用`() {
        val chat = ModelCatalog.glm.chatModels
        assertEquals(Providers.glm.models.map { it.name }, chat.map { it.id })
        assertTrue(chat.all { it.supportsToolCall })
        assertTrue(chat.all { Capability.CHAT in it.capabilities })
    }

    @Test
    fun `mimo 两档 preset 原样并入`() {
        assertEquals("mimo", ModelCatalog.mimo.id)
        assertEquals(Providers.mimo.models.size, ModelCatalog.mimo.chatModels.size)
        assertEquals("mimo-tp", ModelCatalog.mimoTokenPlan.chatPreset?.id)
    }

    @Test
    fun `聊天扩容厂商全部带 OpenAI 兼容端点`() {
        listOf(ModelCatalog.deepseek, ModelCatalog.moonshot, ModelCatalog.dashscope, ModelCatalog.volcArk, ModelCatalog.siliconflow).forEach {
            assertNotNull("厂商 ${it.id} 缺聊天端点", it.chatPreset)
            assertTrue("厂商 ${it.id} 无聊天模型", it.chatModels.isNotEmpty())
            assertTrue(it.chatPreset!!.baseUrl.startsWith("https://"))
        }
    }

    @Test
    fun `deepseek reasoner 不支持工具调用`() {
        val reasoner = ModelCatalog.deepseek.chatModels.first { it.id == "deepseek-reasoner" }
        assertFalse(reasoner.supportsToolCall)
        assertTrue(ModelCatalog.deepseek.chatModels.first { it.id == "deepseek-chat" }.supportsToolCall)
    }

    @Test
    fun `生图模型全部声明协议与张数域`() {
        ModelCatalog.all.flatMap { it.imageModels }.forEach { m ->
            assertNotNull("模型 ${m.id} 缺生图协议", m.imageProtocol)
            assertNotNull("模型 ${m.id} 缺参考图张数域", m.inputImages)
        }
    }

    @Test
    fun `主力组合齐备`() {
        assertNotNull(ModelCatalog.dashscope.model("qwen-image-edit-plus"))
        assertEquals(2, ModelCatalog.volcArk.imageModels.size)
        assertEquals(0..0, ModelCatalog.siliconflow.model("Kwai-Kolors/Kolors")?.inputImages)
        // 硅基通道实测（2026-09-30 live）：image 只收字符串 → 编辑类单图
        assertEquals(1..1, ModelCatalog.siliconflow.model("Qwen/Qwen-Image-Edit")?.inputImages)
        assertTrue(ModelCatalog.volcArk.model("doubao-seedream-4-0-250828")!!.inputImages!!.last >= 14)
    }

    @Test
    fun `aitryon 只收公网 URL`() {
        val spec = ModelCatalog.dashscope.model("aitryon-plus")!!
        assertFalse(spec.supportsBase64)
        assertEquals(ImageProtocol.DASHSCOPE_ASYNC_TASK, spec.imageProtocol)
    }

    @Test
    fun `参数声明默认值均为原始 JSON`() {
        ModelCatalog.all.flatMap { it.imageModels }.flatMap { it.params }.forEach { p ->
            assertTrue("参数 ${p.key} 默认值不是原始 JSON", p.default is JsonPrimitive)
            assertTrue("参数 ${p.key} 默认值为空", p.default.content.isNotBlank() || p.key == "negative_prompt")
            if (p.type == ParamType.ENUM) assertTrue("参数 ${p.key} 缺取值域", p.options.isNotEmpty())
        }
    }

    @Test
    fun `自定义生图厂商构造`() {
        val spec = ProviderSpec.customImage("https://img.example.com/v1/", "my-model")
        assertEquals("custom-image", spec.id)
        assertEquals("https://img.example.com/v1", spec.imageBaseUrl)
        val model = spec.imageModels.single()
        assertEquals("my-model", model.id)
        assertEquals(ImageProtocol.OPENAI_IMAGES_SYNC, model.imageProtocol)
    }

    @Test
    fun `一把 Key 双轨共用的厂商三对`() {
        // 同一 ProviderSpec 声明聊天端点 + 生图端点 = 消费方按 provider.id 存取同一把 Key
        val dual = ModelCatalog.all.filter { it.chatPreset != null && it.imageBaseUrl != null }.map { it.id }
        assertTrue(dual.containsAll(listOf("glm", "dashscope", "volc-ark", "siliconflow")))
    }
    @Test
    fun `聊天双清单一致 - 双写厂商 preset 与 models 完全同步`() {
        // it-081/O-1：ProviderSpec init 已有 require，本测试确保目录实例化即校验
        //（目录若漂移，任一访问 ModelCatalog 的测试都会在此先炸出可读信息）
        ModelCatalog.all.forEach { spec ->
            val chatIds = spec.models.filter { Capability.CHAT in it.capabilities }.map { it.id }.toSet()
            spec.chatPreset?.let { preset ->
                assertEquals("厂商 ${spec.id} preset.id 应与 spec.id 一致", spec.id, preset.id)
                if (chatIds.isNotEmpty()) {
                    assertEquals(
                        "厂商 ${spec.id} 聊天双清单漂移",
                        chatIds,
                        preset.models.map { it.name }.toSet(),
                    )
                }
            }
        }
    }

    @Test
    fun `协议路由工厂 - 异步任务型路由到 Task 适配器`() {
        val provider = ModelCatalog.dashscope
        val keys = com.leo.libs.agent.InMemoryApiKeyStore()
        val task = ImageModel.of(provider, "aitryon-plus", keys)
        assertTrue(task is DashScopeTaskImageModel)
        val sync = ImageModel.of(provider, "qwen-image-edit-plus", keys)
        assertTrue(sync is OkHttpImageModel)
        val unknown = ImageModel.of(provider, "不存在模型", keys)
        assertTrue(unknown is OkHttpImageModel)
    }
}
