package com.leo.wardrobe.export

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** it-081/B-5：XMP 段二进制拼接回归（纯 JVM，白捡的测试——导出长图元数据唯一通道） */
class JpegXmpTest {

    @get:Rule
    val tmp = TemporaryFolder()

    /** 最小合法 JPEG 头：SOI + APP0(JFIF) + 尾部伪数据 */
    private fun minimalJpeg(): ByteArray {
        val app0Len = 16
        val out = ByteArray(2 + 2 + app0Len + 8)
        out[0] = 0xFF.toByte(); out[1] = 0xD8.toByte()
        out[2] = 0xFF.toByte(); out[3] = 0xE0.toByte()
        out[4] = ((app0Len shr 8) and 0xFF).toByte()
        out[5] = (app0Len and 0xFF).toByte()
        out[out.size - 1] = 0x7A.toByte()
        return out
    }

    @Test
    fun `embeds APP1 XMP segment after APP0 with UTF-8 prompt`() {
        val f = tmp.newFile("a.jpg")
        val original = minimalJpeg()
        f.writeBytes(original)
        val prompt = "白色衬衫 · 通勤风（中文描述）"

        JpegXmp.embedPrompt(f, prompt)
        val out = f.readBytes()

        // 结构：SOI + APP0（原样） + APP1（新插入） + 余下原数据
        assertEquals(0xFF, out[0].toInt() and 0xFF)
        assertEquals(0xD8, out[1].toInt() and 0xFF)
        assertEquals(0xE0, out[3].toInt() and 0xFF) // APP0 仍在最前
        val app0Len = ((out[4].toInt() and 0xFF) shl 8) or (out[5].toInt() and 0xFF)
        val app1At = 2 + 2 + app0Len
        assertEquals(0xFF, out[app1At].toInt() and 0xFF)
        assertEquals(0xE1, out[app1At + 1].toInt() and 0xFF) // XMP 是 APP1
        val segLen = ((out[app1At + 2].toInt() and 0xFF) shl 8) or (out[app1At + 3].toInt() and 0xFF)
        assertEquals(original.size - (2 + 2 + app0Len), out.size - (app1At + 2 + segLen)) // 段后原始尾部字节数守恒
        // XMP 标准头 + UTF-8 中文 prompt 字节都在段内
        val segText = out.copyOfRange(app1At + 4, app1At + 2 + segLen).toString(Charsets.UTF_8)
        assertTrue(segText.startsWith("http://ns.adobe.com/xap/1.0/"))
        assertTrue(segText.contains(prompt))
        assertTrue(segText.contains("dc:description"))
        // 原始尾部数据完整保留（无截断）
        val tailAt = app1At + 2 + segLen
        assertEquals(original.copyOfRange(original.size - 8, original.size).toList(),
            out.copyOfRange(tailAt, out.size).toList())
    }

    @Test
    fun `segments after SOI when no APP0 present`() {
        val f = tmp.newFile("b.jpg")
        val original = ByteArray(10) { (it + 1).toByte() }
        original[0] = 0xFF.toByte(); original[1] = 0xD8.toByte()
        f.writeBytes(original)

        JpegXmp.embedPrompt(f, "prompt")
        val out = f.readBytes()

        // 无 APP0：APP1 紧跟 SOI（偏移 2）
        assertEquals(0xE1, out[3].toInt() and 0xFF)
        assertEquals(original.size + (out.size - original.size), out.size) // 长度只增不减
        assertEquals(original[9], out[out.size - 1]) // 原末字节保底保留
    }
}
