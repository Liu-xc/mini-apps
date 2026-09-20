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
    val composeBom = platform("androidx.compose:compose-bom:2025.06.01")
    implementation(composeBom)
    // Material 3（锁定 1.4.0，与 wardrobe ADR-004 同口径）
    implementation("androidx.compose.material3:material3:1.4.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.navigation:navigation-compose:2.8.5")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("androidx.exifinterface:exifinterface:1.3.7")
    // 本地「好久没去」提醒（it-007 阶段B，ADR-013）
    implementation("androidx.work:work-runtime-ktx:2.10.0")

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    implementation("io.coil-kt:coil-compose:2.7.0")
    // 地图：osmdroid + OSM 瓦片，免 API Key（ADR-002）
    implementation("org.osmdroid:osmdroid-android:6.1.20")
    // 侧滑卡组 + 随机抽取 SDK（composite build，libs/carddeck）
    implementation("com.leo.libs:carddeck:0.1.0")
    // 本地存储 SDK（composite build 依赖替换，libs/store）
    implementation("com.leo.libs:store:0.1.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
}
