plugins {
    id("com.android.library") version "8.7.3"
    id("org.jetbrains.kotlin.android") version "2.1.21"
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.21"
}

group = "com.leo.libs"
version = "0.1.0"

android {
    namespace = "com.leo.libs.carddeck"
    compileSdk = 35

    defaultConfig { minSdk = 26 }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.06.01")
    api(composeBom)
    api("androidx.compose.foundation:foundation")
    api("androidx.compose.runtime:runtime")

    // 卡组手势与动画全部来自该库（Apache-2.0，API 见 specs/00-overview.md）；
    // 本 SDK 只做封装与抽取编排，不自研手势动画
    api("com.github.smartword-app:compose-swipeable-cards:1.1.4")
}
