package com.leo.libs.agent

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * it-002 验收：core 为纯 Kotlin（无 `android.*` import），桌面 JVM 全量单测。
 * 工作目录 = 模块根（libs/agent）。
 */
class SourcePurityTest {

    @Test
    fun `main 源码无 android import`() {
        val root = File("src/main/kotlin")
        assertTrue("src/main/kotlin 不存在（工作目录=${File(".").absolutePath}）", root.isDirectory)
        val offenders = root.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filter { it.readText().lines().any { line -> line.trim().startsWith("import android.") } }
            .map { it.path }
            .toList()
        assertTrue("发现 android import：$offenders", offenders.isEmpty())
    }
}
