// 声明插件版本。依赖清单与选型理由见 specs/04-architecture.md、specs/06-decisions.md（与 wardrobe 同基线，ADR-001）
plugins {
    id("com.android.application") version "8.7.3" apply false
    id("org.jetbrains.kotlin.android") version "2.1.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.21" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.1.21" apply false
}
