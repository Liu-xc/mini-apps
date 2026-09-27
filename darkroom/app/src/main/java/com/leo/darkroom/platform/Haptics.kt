package com.leo.darkroom.platform

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * 触感（DESIGN.md §4：关键确认一次轻震；错误警示震）。
 */
object Haptics {

    private fun vibrator(context: Context): Vibrator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)
                ?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }

    /** 关键确认：一次轻震（定影落定、导出完成） */
    fun confirm(context: Context) {
        val v = vibrator(context) ?: return
        if (!v.hasVibrator()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            v.vibrate(VibrationEffect.createOneShot(18, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            v.vibrate(18)
        }
    }

    /** 警示：双短震（导出失败） */
    fun warn(context: Context) {
        val v = vibrator(context) ?: return
        if (!v.hasVibrator()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            v.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 30, 60, 30), -1))
        } else {
            @Suppress("DEPRECATION")
            v.vibrate(longArrayOf(0, 30, 60, 30), -1)
        }
    }
}
