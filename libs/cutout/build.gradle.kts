plugins {
    id("org.jetbrains.kotlin.jvm") version "2.1.21"
}

group = "com.leo.libs"
version = "0.1.0"

kotlin {
    jvmToolchain(17)
}

dependencies {
    // 对外 API 暴露 StateFlow（CutoutEngine.readiness）
    api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
    // ai.onnxruntime Java API：编译期与测试用桌面版；移动端运行时由消费方依赖
    // onnxruntime-android（同一套 Java API，版本需对齐，见 specs/06-decisions.md ADR-002）
    compileOnly("com.microsoft.onnxruntime:onnxruntime:1.20.0")

    testImplementation("com.microsoft.onnxruntime:onnxruntime:1.20.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    testImplementation("junit:junit:4.13.2")
}
