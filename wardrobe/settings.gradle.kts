pluginManagement {
    repositories {
        maven("https://maven.aliyun.com/repository/gradle-plugin")
        maven("https://maven.aliyun.com/repository/google")
        maven("https://maven.aliyun.com/repository/public")
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    // 仓库级统一版本表（it-020）：六个构建共用根 gradle/libs.versions.toml
    versionCatalogs {
        create("libs") { from(files("../gradle/libs.versions.toml")) }
    }
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven("https://maven.aliyun.com/repository/google")
        maven("https://maven.aliyun.com/repository/public")
        google()
        mavenCentral()
        // carddeck SDK 依赖的三方卡组库经 JitPack 分发（composite build 传递依赖在此解析）
        maven("https://jitpack.io")
    }
}

rootProject.name = "wardrobe"
include(":app")

// 公共本地存储 SDK（composite build，见 libs/store/specs/00-architecture.md）
includeBuild("../libs/store")
// 侧滑卡组 + 随机抽取 SDK（见 libs/carddeck/specs/00-overview.md）
includeBuild("../libs/carddeck")
// 主体抠图 SDK（it-016 阶段 B；见 libs/cutout/specs/00-architecture.md）
includeBuild("../libs/cutout")
