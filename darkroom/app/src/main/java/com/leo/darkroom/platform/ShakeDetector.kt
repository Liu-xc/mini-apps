package com.leo.darkroom.platform

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.sqrt

/**
 * 甩一甩检测（it-001 US-3）：加速度计合加速度的帧间增量超阈值 → 回调一次（冷却 450ms）。
 */
class ShakeDetector(
    context: Context,
    private val onShake: () -> Unit,
) : SensorEventListener {

    private val sensorManager =
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    private var lastMagnitude = 0f
    private var lastAt = 0L

    private companion object {
        const val THRESHOLD = 14f // m/s² 帧间增量
        const val COOLDOWN_MS = 450L
    }

    fun start() {
        sensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
    }

    fun stop() {
        sensorManager.unregisterListener(this)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    override fun onSensorChanged(event: SensorEvent) {
        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]
        val magnitude = sqrt(x * x + y * y + z * z)
        val delta = magnitude - lastMagnitude
        lastMagnitude = magnitude
        val now = event.timestamp / 1_000_000L
        if (delta > THRESHOLD && now - lastAt > COOLDOWN_MS) {
            lastAt = now
            onShake()
        }
    }
}
