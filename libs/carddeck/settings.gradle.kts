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
        create("libs") { from(files("../../gradle/libs.versions.toml")) }
    }
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven("https://maven.aliyun.com/repository/public")
        maven("https://maven.aliyun.com/repository/google")
        mavenCentral()
        // 三方卡组动画库经 JitPack 分发（选型见 specs/00-overview.md）
        maven("https://jitpack.io")
    }
}

rootProject.name = "carddeck"
