import java.security.MessageDigest

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

val packPath = providers.gradleProperty("probePack")
val preparePack by tasks.registering(Copy::class) {
    doFirst { require(packPath.isPresent && file(packPath.get()).isFile) { "Pass -PprobePack=/absolute/path/probe.pck" } }
    from(packPath)
    into(layout.buildDirectory.dir("generated/probeAssets"))
    rename { "probe.pck" }
}

android {
    namespace = "com.leo.lottery.engineprobe"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.leo.lottery.engineprobe"
        minSdk = 31
        targetSdk = 35
        versionCode = 1
        versionName = "0.1-probe"
        ndk { abiFilters += "arm64-v8a" }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    sourceSets["main"].assets.srcDir(layout.buildDirectory.dir("generated/probeAssets"))
}
tasks.named("preBuild") { dependsOn(preparePack) }

dependencies {
    val cachedAar = providers.gradleProperty("godotAar").orNull
    if (cachedAar != null) {
        val library = file(cachedAar)
        require(library.isFile) { "Godot AAR file is missing" }
        val hash = MessageDigest.getInstance("SHA-256")
            .digest(library.readBytes()).joinToString("") { "%02x".format(it) }
        require(hash == "e920f3b514907931621f3639b30069fd124ca008d68b366d43a36f41388be8c7") {
            "Requires the exact Maven Central Godot 4.5.2.stable AAR"
        }
        implementation(files(library))
    } else implementation("org.godotengine:godot:4.5.2.stable")
    implementation("androidx.fragment:fragment:1.8.6")
}
