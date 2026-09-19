package com.leo.wardrobe.export

import java.io.File

/**
 * JPEG XMP 嵌入（it-002）：Prompt 元数据唯一通道。
 * XMP = APP1 段内 UTF-8 XML（业界标准，dc:description + exif:UserComment 双字段）。
 * 放弃 EXIF：androidx ExifInterface 写中文必变 '?'，且重建段会把宽高写成 0 破坏兼容。
 */
object JpegXmp {

    private val HEADER = "http://ns.adobe.com/xap/1.0/\u0000".toByteArray(Charsets.US_ASCII)

    fun embedPrompt(file: File, prompt: String) {
        val xml = buildXmp(prompt)
        val segLen = HEADER.size + xml.size
        require(segLen < 0xFFFF) { "XMP 段过长" }
        val segment = ByteArray(2 + 2 + segLen)
        segment[0] = 0xFF.toByte()
        segment[1] = 0xE1.toByte()
        segment[2] = ((segLen shr 8) and 0xFF).toByte()
        segment[3] = (segLen and 0xFF).toByte()
        System.arraycopy(HEADER, 0, segment, 4, HEADER.size)
        System.arraycopy(xml, 0, segment, 4 + HEADER.size, xml.size)

        val src = file.readBytes()
        // 插入点：紧跟 SOI 后的首个 APP0(JFIF) 之后（无 APP0 则紧跟 SOI）。
        // 不再写 EXIF：androidx ExifInterface 会把中文替换为 '?' 且重建段时宽高写 0，
        // 反而破坏兼容性；XMP 是 UTF-8 的标准元数据通道。
        var insertAt = 2
        if (src.size > 4 && (src[2].toInt() and 0xFF) == 0xFF && (src[3].toInt() and 0xFF) == 0xE0) {
            val app0Len = ((src[4].toInt() and 0xFF) shl 8) or (src[5].toInt() and 0xFF)
            insertAt = 2 + 2 + app0Len
        }
        val out = ByteArray(src.size + segment.size)
        System.arraycopy(src, 0, out, 0, insertAt)
        System.arraycopy(segment, 0, out, insertAt, segment.size)
        System.arraycopy(src, insertAt, out, insertAt + segment.size, src.size - insertAt)
        file.writeBytes(out)
    }

    private fun buildXmp(prompt: String): ByteArray {
        val esc = prompt
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
        val xml = buildString {
            append("<?xpacket begin=\"\uFEFF\" id=\"W5M0MpCehiHzreSzNTczkc9d\"?>\n")
            append("<x:xmpmeta xmlns:x=\"adobe:ns:meta/\"><rdf:RDF xmlns:rdf=\"http://www.w3.org/1999/02/22-rdf-syntax-93#\">")
            append("<rdf:Description rdf:about=\"\" ")
            append("xmlns:dc=\"http://purl.org/dc/elements/1.1/\" ")
            append("xmlns:exif=\"http://ns.adobe.com/exif/1.0/\">")
            append("<dc:description><rdf:Alt><rdf:li xml:lang=\"x-default\">")
            append(esc)
            append("</rdf:li></rdf:Alt></dc:description>")
            append("<exif:UserComment>")
            append(esc)
            append("</exif:UserComment>")
            append("</rdf:Description></rdf:RDF></x:xmpmeta>")
            append("<?xpacket end=\"w\"?>")
        }
        return xml.toByteArray(Charsets.UTF_8)
    }
}
