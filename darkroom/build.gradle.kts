// 插件版本统一走根 version catalog（gradle/libs.versions.toml，it-020）
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
