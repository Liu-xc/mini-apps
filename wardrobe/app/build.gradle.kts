plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    // it-071 P3 / ADR-028：release 开 R8 + 基线 profile 链路
    id("androidx.baselineprofile")
}

// it-067 / ADR-027：版本号自动生成——VERSION_BASE.<wardrobe 提交数>，脏树加 -dirty
val versionBase = "0.5.0"
fun gitOut(vararg args: String): String? = try {
    providers.exec {
        workingDir = rootProject.projectDir // wardrobe/，pathspec "." 自此限定衣橱范围
        commandLine("git", *args)
    }.standardOutput.asText.get().trim()
} catch (_: Exception) {
    null // 无 git / 非仓库（如源码 zip 构建）→ 回退基线常量，不阻断构建
}
val wardrobeCommits = gitOut("rev-list", "--count", "HEAD", "--", ".")?.toIntOrNull()
val treeDirty = gitOut("status", "--porcelain", "--", ".")?.isNotEmpty() == true

android {
    namespace = "com.leo.wardrobe"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.leo.wardrobe"
        minSdk = 26
        targetSdk = 35
        versionCode = wardrobeCommits ?: 5
        versionName = buildString {
            append(versionBase)
            if (wardrobeCommits != null) {
                append('.')
                append(wardrobeCommits)
                if (treeDirty) append("-dirty")
            }
        }
        vectorDrawables { useSupportLibrary = true }
        // it-045：-PdemoDefault=true 出「演示数据体验包」——新装即进演示模式；
        // 常规构建恒为 false，行为与 it-015 完全一致（设置页/5 连点仍可双向切换）
        buildConfigField(
            "boolean",
            "DEMO_DEFAULT",
            (providers.gradleProperty("demoDefault").orNull == "true").toString(),
        )
    }

    buildTypes {
        // it-016：release 只保留真机 ABI，onnxruntime .so 体积减半（debug 保留 x86_64 供模拟器评审）
        release {
            ndk { abiFilters.addAll(listOf("arm64-v8a", "armeabi-v7a")) }
            // it-071 P3 / ADR-028：R8 代码+资源收缩首开；个人分发用 debug keystore 签名
            // （与既有 debug 装机同签名可直接覆盖升级，不新增密钥管理面；不可上架 Google Play）
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("debug")
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

    // it-071 P3 / ADR-028：安装时读取 baseline profile（AOT 编译热路径）
    implementation(libs.androidx.profileinstaller)
    // 基线 profile 产出（:baselineprofile 场景生成，merge 进 release 资产）
    baselineProfile(project(":baselineprofile"))

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
