plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "com.leo.lottery"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.leo.lottery"
        minSdk = 31
        targetSdk = 35
        versionCode = 2
        versionName = "0.2.0"
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
        // 与 wardrobe it-016 / darkroom 同因：lint 与 Kotlin 2.1.21 Analysis API 不匹配
        checkReleaseBuilds = false
    }
}

dependencies {
    implementation(platform(libs.compose.bom))
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
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    // it-003 / ADR-007：LIT 物理舞台；材质预编译在 assets，移除运行时编译器。
    implementation(libs.filament.android)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
