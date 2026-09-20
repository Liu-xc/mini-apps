// 插件版本统一走根 version catalog（gradle/libs.versions.toml，it-020）。依赖清单与选型理由见 specs/04-architecture.md、specs/06-decisions.md（与 wardrobe 同基线，ADR-001）
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}
