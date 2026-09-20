plugins {
    alias(libs.plugins.kotlin.jvm)
}

group = "com.leo.libs"
version = "0.1.0"

kotlin {
    jvmToolchain(17)
}

dependencies {
    // 对外 API 暴露 StateFlow（CutoutEngine.readiness）
    api(libs.kotlinx.coroutines.core)
    // ai.onnxruntime Java API：编译期与测试用桌面版；移动端运行时由消费方依赖
    // onnxruntime-android（同一套 Java API，版本需对齐，见 specs/06-decisions.md ADR-002）
    compileOnly(libs.onnxruntime)

    testImplementation(libs.onnxruntime)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.junit)
}
