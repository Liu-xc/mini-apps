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
        // it-047：carddeck 自研内核后仓库不再有 JitPack 构件（原三方卡组库已删）
    }
}

rootProject.name = "wardrobe"
include(":app")
// it-071 P3 / ADR-028：基线 profile 生成模块（Macrobenchmark 场景 → 回灌 :app）
include(":baselineprofile")

// 公共本地存储 SDK（composite build，见 libs/store/specs/00-architecture.md）
includeBuild("../libs/store")
// 侧滑卡组 + 随机抽取 SDK（见 libs/carddeck/specs/00-overview.md）
includeBuild("../libs/carddeck")
// 主体抠图 SDK（it-016 阶段 B；见 libs/cutout/specs/00-architecture.md）
includeBuild("../libs/cutout")
// BYOK Agent SDK（it-041 阶段 A；见 libs/agent/specs/00-architecture.md）
includeBuild("../libs/agent")
