// 插件版本统一走根 version catalog（gradle/libs.versions.toml，it-020）。依赖清单与选型理由见 specs/04-architecture.md、specs/06-decisions.md
plugins {
    alias(libs.plugins.android.application) apply false
    // it-071 P3 / ADR-028：基线 profile 生成模块（com.android.test）与插件本体
    alias(libs.plugins.android.test) apply false
    alias(libs.plugins.androidx.baselineprofile) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}
