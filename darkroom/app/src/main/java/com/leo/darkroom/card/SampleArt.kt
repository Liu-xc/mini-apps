package com.leo.darkroom.card

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * 内置示例「照片」（it-001 AC8 零权限入口）：程序化绘制的三张装饰性风景，
 * 免相册/相机权限即可体验完整显影-成片流程；亦用作选图页空态插画。
 */
object SampleArt {

    val titles = listOf("暮色山径", "海边午后", "城市霓虹")

    fun render(index: Int, size: Int = 1200): Bitmap {
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        when (index.mod(titles.size)) {
            0 -> duskTrail(canvas, size)
            1 -> seasideNoon(canvas, size)
            else -> neonCity(canvas, size)
        }
        return bmp
    }

    /** 暮色山径：橙紫渐变天 + 落日 + 层叠山影 */
    private fun duskTrail(canvas: Canvas, size: Int) {
        val sky = Paint().apply {
            shader = LinearGradient(
                0f, 0f, 0f, size * 0.68f,
                intArrayOf(0xFFF6D9A0.toInt(), 0xFFE0906B.toInt(), 0xFF8A5478.toInt()),
                floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP,
            )
        }
        canvas.drawRect(0f, 0f, size.toFloat(), size * 0.68f, sky)
        canvas.drawRect(0f, size * 0.66f, size.toFloat(), size.toFloat(), Paint().apply { color = 0xFF2C2233.toInt() })

        // 落日 + 光晕
        val sunX = size * 0.62f
        val sunY = size * 0.40f
        canvas.drawCircle(sunX, sunY, size * 0.16f, Paint().apply {
            shader = RadialGradient(
                sunX, sunY, size * 0.16f,
                intArrayOf(0xB4FFE9C9.toInt(), 0x00FFE9C9.toInt()), floatArrayOf(0f, 1f), Shader.TileMode.CLAMP,
            )
        })
        canvas.drawCircle(sunX, sunY, size * 0.085f, Paint().apply { color = 0xFFFFE9CB.toInt() })

        // 层叠山影
        val rng = Random(7)
        var baseY = size * 0.52f
        for (layer in 0 until 3) {
            val paint = Paint().apply {
                color = intArrayOf(0xFF6E4470.toInt(), 0xFF4A3355.toInt(), 0xFF322542.toInt())[layer]
            }
            val path = android.graphics.Path()
            path.moveTo(0f, size.toFloat())
            var x = 0f
            var y = baseY
            path.lineTo(0f, y)
            while (x < size) {
                val step = size * (0.16f + rng.nextFloat() * 0.14f)
                x += step
                y = baseY + (rng.nextFloat() - 0.5f) * size * 0.07f
                path.lineTo(x.coerceAtMost(size.toFloat()), y.coerceIn(baseY - size * 0.06f, baseY + size * 0.05f))
            }
            path.lineTo(size.toFloat(), size.toFloat())
            path.close()
            canvas.drawPath(path, paint)
            baseY += size * 0.10f
        }
    }

    /** 海边午后：晴空 + 海平线 + 白帆 */
    private fun seasideNoon(canvas: Canvas, size: Int) {
        val horizon = size * 0.58f
        canvas.drawRect(0f, 0f, size.toFloat(), horizon, Paint().apply {
            shader = LinearGradient(
                0f, 0f, 0f, horizon,
                0xFFBFE3E0.toInt(), 0xFFEAF6EF.toInt(), Shader.TileMode.CLAMP,
            )
        })
        canvas.drawRect(0f, horizon, size.toFloat(), size.toFloat(), Paint().apply {
            shader = LinearGradient(
                0f, horizon, 0f, size.toFloat(),
                0xFF7FBFB7.toInt(), 0xFF5D9E9A.toInt(), Shader.TileMode.CLAMP,
            )
        })
        // 波光
        val rng = Random(11)
        val wave = Paint().apply { color = 0x66FFFFFF }
        repeat(26) {
            val y = horizon + rng.nextFloat() * (size - horizon)
            val w = size * (0.03f + rng.nextFloat() * 0.10f)
            val x = rng.nextFloat() * (size - w)
            canvas.drawRect(x, y, x + w, y + size * 0.006f, wave)
        }
        // 太阳
        canvas.drawCircle(size * 0.30f, size * 0.20f, size * 0.075f, Paint().apply { color = 0xFFFFF6DC.toInt() })
        // 白帆小船
        val boatX = size * 0.66f
        val boatY = horizon + size * 0.10f
        val sail = android.graphics.Path().apply {
            moveTo(boatX, boatY - size * 0.16f)
            lineTo(boatX, boatY - size * 0.02f)
            lineTo(boatX - size * 0.10f, boatY - size * 0.02f)
            close()
        }
        canvas.drawPath(sail, Paint().apply { color = 0xFFFCFAF2.toInt() })
        canvas.drawRect(
            boatX - size * 0.07f, boatY - size * 0.016f,
            boatX + size * 0.05f, boatY, Paint().apply { color = 0xFF3E5A5A.toInt() },
        )
    }

    /** 城市霓虹：夜空楼影 + 亮窗 + 霓虹环 */
    private fun neonCity(canvas: Canvas, size: Int) {
        canvas.drawRect(0f, 0f, size.toFloat(), size.toFloat(), Paint().apply { color = 0xFF1B1E2A.toInt() })
        val rng = Random(23)
        // 楼影 + 亮窗
        var x = 0f
        while (x < size) {
            val w = size * (0.10f + rng.nextFloat() * 0.12f)
            val h = size * (0.28f + rng.nextFloat() * 0.34f)
            val top = size - h
            canvas.drawRect(x, top, x + w, size.toFloat(), Paint().apply { color = 0xFF12141D.toInt() })
            val winPaint = Paint().apply { color = 0xF2F5D76E.toInt() }
            val cols = (w / (size * 0.028f)).toInt().coerceAtLeast(1)
            val rows = (h / (size * 0.04f)).toInt().coerceAtLeast(1)
            for (c in 0 until cols) {
                for (r in 0 until rows) {
                    if (rng.nextFloat() < 0.42f) {
                        val wx = x + size * 0.014f + c * size * 0.028f
                        val wy = top + size * 0.022f + r * size * 0.04f
                        if (wx + size * 0.012f < x + w && wy < size * 0.99f) {
                            canvas.drawRect(wx, wy, wx + size * 0.012f, wy + size * 0.016f, winPaint)
                        }
                    }
                }
            }
            x += w + size * 0.012f
        }
        // 霓虹环 + 光晕
        val nx = size * 0.32f
        val ny = size * 0.26f
        canvas.drawCircle(nx, ny, size * 0.11f, Paint().apply {
            shader = RadialGradient(
                nx, ny, size * 0.15f,
                intArrayOf(0x55FF6B9D, 0x00FF6B9D), floatArrayOf(0f, 1f), Shader.TileMode.CLAMP,
            )
        })
        canvas.drawCircle(nx, ny, size * 0.075f, Paint().apply {
            style = Paint.Style.STROKE
            strokeWidth = size * 0.014f
            color = 0xFFFF8FB4.toInt()
        })
        canvas.drawLine(
            size * 0.62f, size * 0.16f, min(size * 0.62f + 4f, size.toFloat()), size * 0.16f,
            Paint().apply { color = 0xFF2A2E40.toInt(); strokeWidth = size * 0.01f },
        )
    }
}
