// it-071 P3 / ADR-028：基线 profile 生成模块（Macrobenchmark 场景 → 回灌 :app release 资产）。
// 测试代码在 src/main（com.android.test 模块即测试模块本体）；只启用 release 变体
// （debuggable 包产出的 profile 无效）。生成：./gradlew :baselineprofile:generateReleaseBaselineProfile
plugins {
    id("com.android.test")
    id("org.jetbrains.kotlin.android")
    id("androidx.baselineprofile")
}

android {
    namespace = "com.leo.wardrobe.baselineprofile"
    compileSdk = 35

    defaultConfig {
        // Macrobenchmark / UiAutomator 下限
        minSdk = 28
        targetSdk = 35
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    targetProjectPath = ":app"
    experimentalProperties["android.experimental.self-instrumenting"] = true
}

dependencies {
    implementation(libs.androidx.test.ext.junit)
    implementation(libs.androidx.test.uiautomator)
    implementation(libs.androidx.benchmark.macro.junit4)
}

// it-071：不手动过滤变体——androidx.baselineprofile 插件自建 benchmarkRelease/nonMinifiedRelease
// 测试变体（debug 默认禁用）；手写 buildType=="release" 过滤会把它们全关掉，生成任务空跑。
