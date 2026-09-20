plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
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
    api(platform(libs.compose.bom))
    api(libs.compose.foundation)
    api(libs.compose.runtime)

    // 卡组手势与动画全部来自该库（Apache-2.0，API 见 specs/00-overview.md）；
    // 本 SDK 只做封装与抽取编排，不自研手势动画
    api(libs.swipeable.cards)
}
