package com.leo.wardrobe.data.gen

import com.leo.libs.agent.ImageParamSpec
import com.leo.libs.agent.ModelSpec
import com.leo.libs.agent.ParamType
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageParamEncoderTest {

    private val model = ModelSpec(
        id = "test/image-edit",
        capabilities = emptySet(),
        params = listOf(
            ImageParamSpec("seed", "种子", ParamType.INT, default = JsonPrimitive(0)),
            ImageParamSpec("guidance", "贴合度", ParamType.FLOAT, default = JsonPrimitive(2.5)),
            ImageParamSpec("watermark", "水印", ParamType.BOOL, default = JsonPrimitive(false)),
            ImageParamSpec("size", "尺寸", ParamType.ENUM, listOf("1024x1024"), JsonPrimitive("1024x1024")),
            ImageParamSpec("negative_prompt", "负向词", ParamType.TEXT, default = JsonPrimitive("")),
        ),
    )

    @Test
    fun `declared parameter types are preserved in JSON`() {
        val encoded = encodeImageParams(
            model.params,
            mapOf(
                "seed" to "17",
                "guidance" to "2.5",
                "watermark" to "false",
                "size" to "1024x1024",
                "negative_prompt" to "logo",
            ),
        )

        assertEquals("17", encoded.getValue("seed").toString())
        assertFalse(encoded.getValue("seed").jsonPrimitive.isString)
        assertEquals("2.5", encoded.getValue("guidance").toString())
        assertFalse(encoded.getValue("guidance").jsonPrimitive.isString)
        assertEquals("false", encoded.getValue("watermark").toString())
        assertFalse(encoded.getValue("watermark").jsonPrimitive.isString)
        assertTrue(encoded.getValue("size").jsonPrimitive.isString)
        assertTrue(encoded.getValue("negative_prompt").jsonPrimitive.isString)
    }

    @Test
    fun `invalid declared numeric parameter falls back to its default`() {
        // it-081/A-13：非法值不再被丢弃而是回退 default
        val encoded = encodeImageParams(model.params, mapOf("seed" to "", "watermark" to "yes"))

        assertEquals("0", encoded.getValue("seed").toString())
        assertEquals("false", encoded.getValue("watermark").toString())
    }

    @Test
    fun `int below declared min is omitted for server-side default`() {
        // it-082：Kolors seed=-1（随机语义）+ min=0（SiliconFlow 20015 要求 ≥0）→ 省略字段
        val spec = ImageParamSpec("seed", "种子", ParamType.INT, default = JsonPrimitive(-1), min = 0.0)

        val omitted = encodeImageParams(listOf(spec), mapOf("seed" to "-1"))
        assertFalse("低于 min 的 INT 应省略字段", omitted.containsKey("seed"))

        val ok = encodeImageParams(listOf(spec), mapOf("seed" to "5"))
        assertEquals("5", ok.getValue("seed").toString())

        // 未声明 min 的模型（DashScope 系）-1 是官方随机语义，原样透传
        val noMin = ImageParamSpec("seed", "种子", ParamType.INT, default = JsonPrimitive(-1))
        assertEquals(-1L, encodeImageParams(listOf(noMin), mapOf("seed" to "-1")).getValue("seed").jsonPrimitive.long)
    }
}
