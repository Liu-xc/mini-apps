package com.leo.libs.sync.bitable

import com.leo.libs.sync.SyncValue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ValueCodecTest {

    private fun roundtrip(type: Int, value: SyncValue): SyncValue {
        val encoded = ValueCodec.encode(value)
        val json = Json.parseToJsonElement(
            """{"f":${Json.encodeToString(kotlinx.serialization.json.JsonElement.serializer(), encoded)}}""",
        ).jsonObject["f"]!!
        return ValueCodec.decode(type, json)
    }

    @Test
    fun textRoundtrip() {
        assertEquals(SyncValue.Text("白衬衫"), roundtrip(ValueCodec.TYPE_TEXT, SyncValue.Text("白衬衫")))
        assertEquals(SyncValue.Text(""), roundtrip(ValueCodec.TYPE_TEXT, SyncValue.Text(null)))
    }

    @Test
    fun numberRoundtrip() {
        assertEquals(SyncValue.Number(1234.0), roundtrip(ValueCodec.TYPE_NUMBER, SyncValue.Number(1234.0)))
        assertEquals(SyncValue.Number(null), roundtrip(ValueCodec.TYPE_NUMBER, SyncValue.Number(null)))
    }

    @Test
    fun boolAndInstantRoundtrip() {
        assertEquals(SyncValue.Bool(true), roundtrip(ValueCodec.TYPE_CHECKBOX, SyncValue.Bool(true)))
        assertEquals(SyncValue.Instant(1695200000000L), roundtrip(ValueCodec.TYPE_DATE, SyncValue.Instant(1695200000000L)))
    }

    @Test
    fun optionsRoundtrip() {
        val v = SyncValue.Options(listOf("通勤", "简约"))
        assertEquals(v, roundtrip(ValueCodec.TYPE_MULTI_SELECT, v))
    }

    @Test
    fun singleSelectStringDecodesToOptions() {
        // 飞书单选列返回裸字符串
        val decoded = ValueCodec.decode(ValueCodec.TYPE_SINGLE_SELECT, kotlinx.serialization.json.JsonPrimitive("上装"))
        assertEquals(SyncValue.Options(listOf("上装")), decoded)
    }

    @Test
    fun textSegmentsJoined() {
        // 多行文本在部分接口返回段数组 [{type:text,text:…}]
        val element = Json.parseToJsonElement("""[{"type":"text","text":"a"},{"type":"text","text":"b"}]""")
        assertEquals("ab", ValueCodec.joinTextSegments(element.jsonArray))
    }

    @Test
    fun attachmentRoundtrip() {
        // 写方向只传 file_token（bitable 协议），meta 不上行——读方向由真实响应带回
        val v = SyncValue.Attachment("tok123")
        val back = roundtrip(ValueCodec.TYPE_ATTACHMENT, v)
        assertEquals("tok123", (back as SyncValue.Attachment).ref)
    }

    @Test
    fun localRefEncodesAsEmptyArray() {
        val encoded = ValueCodec.encode(SyncValue.Attachment("local:uuid.webp"))
        assertTrue(encoded.jsonArray.isEmpty())
    }

    @Test
    fun attachmentDecodedFromRealShape() {
        // 真实读取形态：[{file_token, name, size, url}]
        val element = Json.parseToJsonElement(
            """[{"file_token":"ft1","name":"x.webp","size":1024,"url":"https://..."}]""",
        )
        val decoded = ValueCodec.decode(ValueCodec.TYPE_ATTACHMENT, element)
        val att = decoded as SyncValue.Attachment
        assertEquals("ft1", att.ref)
        assertEquals(1024L, att.meta.sizeBytes)
    }

    @Test
    fun unknownFieldTypeFallsBackToJsonText() {
        // 用户在飞书 UI 里加的公式/关联等字段 → JsonText，不炸同步
        val element = Json.parseToJsonElement("""{"some":"obj"}""")
        val decoded = ValueCodec.decode(null, element)
        assertTrue(decoded is SyncValue.JsonText)
    }

    @Test
    fun inferFieldTypeMatchesContract() {
        assertEquals(ValueCodec.TYPE_TEXT, ValueCodec.inferFieldType(SyncValue.Text("a")))
        assertEquals(ValueCodec.TYPE_NUMBER, ValueCodec.inferFieldType(SyncValue.Number(1.0)))
        assertEquals(ValueCodec.TYPE_CHECKBOX, ValueCodec.inferFieldType(SyncValue.Bool(false)))
        assertEquals(ValueCodec.TYPE_DATE, ValueCodec.inferFieldType(SyncValue.Instant(1L)))
        assertEquals(ValueCodec.TYPE_MULTI_SELECT, ValueCodec.inferFieldType(SyncValue.Options(listOf("x"))))
        assertEquals(ValueCodec.TYPE_ATTACHMENT, ValueCodec.inferFieldType(SyncValue.Attachment("local:a")))
        assertEquals(ValueCodec.TYPE_TEXT, ValueCodec.inferFieldType(SyncValue.JsonText("[]")))
    }
}
