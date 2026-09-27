package com.leo.darkroom

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * it-002 AC1 / P0 防回归：定影落定触感走 `Vibrator.vibrate()`，
 * manifest 缺 VIBRATE 会在 `Haptics.confirm` 抛 SecurityException 使显影必崩
 * （2026-09-27 审查实锤，logcat 复现 3 次）。
 */
class ManifestGuardTest {

    @Test
    fun `manifest declares VIBRATE permission`() {
        val manifest = listOf(
            File("src/main/AndroidManifest.xml"),
            File("app/src/main/AndroidManifest.xml"),
        ).firstOrNull { it.exists() }
            ?: error("AndroidManifest.xml not found from ${File(".").absolutePath}")

        val xml = manifest.readText()
        assertTrue(
            "AndroidManifest.xml must declare android.permission.VIBRATE " +
                "(Haptics.confirm → Vibrator.vibrate)",
            xml.contains("android.permission.VIBRATE"),
        )
    }
}
