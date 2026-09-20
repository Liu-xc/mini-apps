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

rootProject.name = "eats"
include(":app")
includeBuild("../libs/carddeck")
includeBuild("../libs/store")
