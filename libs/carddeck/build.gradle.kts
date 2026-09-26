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

    // it-047：卡组手势/动画为本 SDK 自研内核（官方 AnchoredDraggable + spring），
    // 不再依赖三方 swipeable-cards（选型依据见 wardrobe specs/06-decisions）
}
