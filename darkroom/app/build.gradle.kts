plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.leo.darkroom"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.leo.darkroom"
        // it-001 ADR-001：minSdk 31 吃 RenderEffect（预览模糊走系统 BlurEffect），
        // 覆盖更老设备的软件路径不在 MVP 范围
        minSdk = 31
        targetSdk = 35
        versionCode = 7
        versionName = "0.3.0"
        vectorDrawables { useSupportLibrary = true }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
    packaging { resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" } }

    lint {
        // 与 wardrobe it-016 同因：lint 工具与 Kotlin 2.1.21 的 Analysis API 不匹配，
        // release 内置 lintVital 多个 detector 分析即崩；个人应用不阻塞发布，日常 lint 手动跑
        checkReleaseBuilds = false
    }
}

dependencies {
    implementation(platform(libs.compose.bom))
    // Material 3 Expressive（锁定 1.4.0，对齐 wardrobe ADR-004；BOM 内为 1.3.x，显式覆盖）
    implementation(libs.compose.material3)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.animation)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material.icons.extended)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.exifinterface)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
