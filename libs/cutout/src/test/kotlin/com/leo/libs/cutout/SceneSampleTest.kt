package com.leo.libs.cutout

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Graphics2D
import java.awt.Polygon
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.random.Random
import kotlin.time.Duration.Companion.INFINITE

/**
 * 真实场景样张自测（it-016 阶段 A 报告用，兼质量回归）：
 * 合成 4 类典型衣物拍摄场景 → 真模型推理 → 输出 抠图前后 PNG（透明底以棋盘格呈现）
 * 到 build/cutout-samples/，并对主体保留/背景移除做断言。
 */
class SceneSampleTest {

    private val w = 720
    private val h = 900

    /** T 恤形多边形（归一化坐标）：领口 + 左右袖 + 衣身 */
    private val tee = arrayOf(
        0.40f to 0.06f, 0.45f to 0.02f, 0.50f to 0.07f, 0.55f to 0.02f, 0.60f to 0.06f,
        0.80f to 0.18f, 0.88f to 0.44f, 0.77f to 0.50f, 0.70f to 0.35f,
        0.70f to 0.93f, 0.30f to 0.93f, 0.30f to 0.35f, 0.23f to 0.50f, 0.12f to 0.44f, 0.20f to 0.18f,
    )

    private fun teePolygon(g: Graphics2D, color: Color, stroke: Color? = null) {
        val p = Polygon()
        tee.forEach { (x, y) -> p.addPoint((x * w).toInt(), (y * h).toInt()) }
        g.color = color
        g.fillPolygon(p)
        stroke?.let {
            g.color = it
            g.stroke = BasicStroke(5f)
            g.drawPolygon(p)
        }
    }

    private fun gradientNoise(
        top: Color,
        bottom: Color,
        seed: Long,
        noise: Int = 12,
        paint: (Graphics2D) -> Unit = {},
    ): ByteArray {
        val img = BufferedImage(w, h, BufferedImage.TYPE_INT_RGB)
        val g = img.createGraphics()
        for (y in 0 until h) {
            val t = y.toFloat() / h
            g.color = Color(
                (top.red + (bottom.red - top.red) * t).toInt(),
                (top.green + (bottom.green - top.green) * t).toInt(),
                (top.blue + (bottom.blue - top.blue) * t).toInt(),
            )
            g.drawLine(0, y, w, y)
        }
        paint(g)
        g.dispose()
        val rnd = Random(seed)
        val rgba = ByteArray(w * h * 4)
        for (i in 0 until w * h) {
            val n = rnd.nextInt(-noise, noise + 1)
            val rgb = img.getRGB(i % w, i / w)
            rgba[i * 4] = (((rgb shr 16) and 0xFF) + n).coerceIn(0, 255).toByte()
            rgba[i * 4 + 1] = (((rgb shr 8) and 0xFF) + n).coerceIn(0, 255).toByte()
            rgba[i * 4 + 2] = ((rgb and 0xFF) + n).coerceIn(0, 255).toByte()
            rgba[i * 4 + 3] = 255.toByte()
        }
        return rgba
    }

    private fun toImage(rgba: ByteArray, checker: Boolean): BufferedImage {
        val img = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
        for (y in 0 until h) {
            for (x in 0 until w) {
                val i = (y * w + x) * 4
                val a = rgba[i + 3].toInt() and 0xFF
                if (checker && a == 0) {
                    img.setRGB(x, y, if ((x / 20 + y / 20) % 2 == 0) -0x111112 else -0x1)
                } else {
                    img.setRGB(
                        x, y,
                        ((rgba[i].toInt() and 0xFF) shl 16) or ((rgba[i + 1].toInt() and 0xFF) shl 8) or
                            (rgba[i + 2].toInt() and 0xFF) or (a shl 24),
                    )
                }
            }
        }
        return img
    }

    private fun write(name: String, rgba: ByteArray, checker: Boolean) {
        val dir = File("build/cutout-samples").apply { mkdirs() }
        ImageIO.write(toImage(rgba, checker), "png", File(dir, name))
    }

    @Test
    fun `scene samples cut out with quality assertions`() = runBlocking {
        val modelBytes = javaClass.classLoader.getResourceAsStream("u2netp.onnx")!!.use { it.readBytes() }
        val engine = OnnxCutoutEngine({ modelBytes }, idleTimeout = INFINITE)

        data class Scene(
            val id: String,
            val label: String,
            val rgba: ByteArray,
            val subjectProbe: Pair<Float, Float>,   // 主体应保留（画面中心附近）
            val extraProbe: Pair<Float, Float>? = null, // 额外保留探针（如人穿衣服的头部）
        )
        val scenes = listOf(
            Scene(
                "flatLay", "平铺·杂色渐变床单",
                gradientNoise(Color(0x8a, 0x93, 0xa5), Color(0x5d, 0x66, 0x77), seed = 1) { g ->
                    teePolygon(g, Color(0xb5, 0x49, 0x3f), stroke = Color(0x8f, 0x38, 0x30))
                    g.color = Color(0xf2, 0xe8, 0xdc)
                    g.fillRect((0.44f * w).toInt(), (0.40f * h).toInt(), (0.12f * w).toInt(), (0.30f * h).toInt())
                },
                0.32f * w to 0.60f * h,
            ),
            Scene(
                "hanger", "挂拍·木地板背景",
                gradientNoise(Color(0xc9, 0xa8, 0x77), Color(0xa8, 0x87, 0x5a), seed = 2) { g ->
                    g.color = Color(0x6f, 0x6f, 0x74)
                    g.fillRect(w / 2 - 4, 0, 8, (0.06f * h).toInt())
                    teePolygon(g, Color(0x2e, 0x3a, 0x52), stroke = Color(0x22, 0x2c, 0x40))
                },
                0.32f * w to 0.60f * h,
            ),
            Scene(
                "lowContrast", "低对比·白衬衫浅灰背景",
                gradientNoise(Color(0xd8, 0xd8, 0xdc), Color(0xc0, 0xc0, 0xc8), seed = 3, noise = 5) { g ->
                    teePolygon(g, Color(0xf4, 0xf4, 0xf6), stroke = Color(0xdc, 0xdc, 0xe2))
                },
                0.32f * w to 0.60f * h,
            ),
            Scene(
                "onBody", "人穿衣服·预期连人抠出",
                gradientNoise(Color(0xb9, 0xc4, 0xb2), Color(0x8f, 0x9e, 0x89), seed = 4) { g ->
                    g.color = Color(0xd9, 0xa8, 0x86)                       // 头
                    g.fillOval((0.40f * w).toInt(), (0.02f * h).toInt(), (0.20f * w).toInt(), (0.16f * h).toInt())
                    g.color = Color(0xd9, 0xa8, 0x86)                       // 手臂
                    g.fillRect((0.13f * w).toInt(), (0.30f * h).toInt(), (0.07f * w).toInt(), (0.42f * h).toInt())
                    g.fillRect((0.80f * w).toInt(), (0.30f * h).toInt(), (0.07f * w).toInt(), (0.42f * h).toInt())
                    teePolygon(g, Color(0x37, 0x5a, 0x7a))                  // 衣服
                },
                0.32f * w to 0.60f * h,
                0.50f * w to 0.09f * h,                                     // 头部也应保留（连人抠出）
            ),
        )

        val lines = mutableListOf<String>()
        for (s in scenes) {
            val out = engine.cutout(s.rgba.copyOf(), w, h)
            fun alpha(x: Float, y: Float) = out[((y.toInt() * w) + x.toInt()) * 4 + 3].toInt() and 0xFF
            val subject = alpha(s.subjectProbe.first, s.subjectProbe.second)
            val corner = alpha(6f, 6f)
            val extra = s.extraProbe?.let { alpha(it.first, it.second) }
            lines += "${s.label}: subjectAlpha=$subject cornerAlpha=$corner" +
                (extra?.let { " extraProbeAlpha=$it" } ?: "")
            write("${s.id}-original.png", s.rgba, checker = false)
            write("${s.id}-cutout.png", out, checker = true)
            assertTrue("${s.id} subject kept (alpha=$subject)", subject > 150)
            assertTrue("${s.id} background removed (alpha=$corner)", corner < 100)
            extra?.let { assertTrue("${s.id} person kept with clothes (alpha=$it)", it > 150) }
        }
        println(lines.joinToString("\n"))
    }
}
