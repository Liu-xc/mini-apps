plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "com.leo.eats"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.leo.eats"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        vectorDrawables { useSupportLibrary = true }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures {
        compose = true
        buildConfig = true // it-006：演示模式入口按 DEBUG 构建显隐
    }
    packaging { resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" } }

    lint {
        // lint 工具与 Kotlin 2.1.21 的 Analysis API 不匹配：release 内置 lintVital 分析即崩
        // （lifecycle NonNullableMutableLiveDataDetector 等，与业务代码无关；首次出 release 包时
        // 暴露，与 wardrobe it-016 同因同修）。个人应用不阻塞发布，日常 lint 手动跑。
        checkReleaseBuilds = false
    }
}

dependencies {
    implementation(platform(libs.compose.bom))
    // Material 3（锁定 1.4.0，与 wardrobe ADR-004 同口径）
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
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.exifinterface)
    // 本地「好久没去」提醒（it-007 阶段B，ADR-013）
    implementation(libs.androidx.work.runtime.ktx)

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.coil.compose)
    // 地图：osmdroid + OSM 瓦片，免 API Key（ADR-002）
    implementation(libs.osmdroid.android)
    // 侧滑卡组 + 随机抽取 SDK（composite build，libs/carddeck）
    implementation(libs.leo.carddeck)
    // 本地存储 SDK（composite build 依赖替换，libs/store）
    implementation(libs.leo.store)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
