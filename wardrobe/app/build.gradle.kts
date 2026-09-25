plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "com.leo.wardrobe"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.leo.wardrobe"
        minSdk = 26
        targetSdk = 35
        versionCode = 5
        versionName = "0.5.0"
        vectorDrawables { useSupportLibrary = true }
    }

    buildTypes {
        // it-016：release 只保留真机 ABI，onnxruntime .so 体积减半（debug 保留 x86_64 供模拟器评审）
        release {
            ndk { abiFilters.addAll(listOf("arm64-v8a", "armeabi-v7a")) }
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
        buildConfig = true // it-015：演示模式入口按 DEBUG 构建显隐
    }
    packaging { resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" } }

    lint {
        // lint 工具与 Kotlin 2.1.21 的 Analysis API 不匹配：release 内置 lintVital 多个
        // detector 分析即崩（lifecycle NonNullableMutableLiveDataDetector 等，与业务代码无关，
        // it-016 阶段 B 首次出 release 包时暴露）。个人应用不阻塞发布，日常 lint 手动跑。
        checkReleaseBuilds = false
    }
}

dependencies {
    implementation(platform(libs.compose.bom))
    // Material 3 Expressive（锁定 1.4.0，见 ADR-004；BOM 内为 1.3.x，显式覆盖）
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

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    // 本地存储 SDK（composite build 依赖替换，libs/store）
    implementation(libs.leo.store)
    // 侧滑卡组 + 随机抽取 SDK（composite build，libs/carddeck）
    implementation(libs.leo.carddeck)
    // 主体抠图 SDK（it-016，composite build，libs/cutout）+ 移动端 ONNX 运行时
    // （版本须与 libs/cutout 的 compileOnly 桌面版对齐，见 libs/cutout/specs/06-decisions.md ADR-002）
    implementation(libs.leo.cutout)
    implementation(libs.onnxruntime.android)
    // BYOK Agent SDK（it-041 阶段 A，composite build，libs/agent）
    implementation(libs.leo.agent)

    implementation(libs.coil.compose)
    implementation(libs.lottie.compose)
    // 本地「好久没穿」提醒（it-018 阶段C，ADR-019）
    implementation(libs.androidx.work.runtime.ktx)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
