// Shared code, compiled here only to check it stays free of loader APIs.
// Each loader module compiles these sources itself.
plugins {
    id("org.jetbrains.kotlin.jvm")
    id("net.neoforged.moddev")
    id("dev.detekt")
}

neoForge {
    neoFormVersion = "26.1.2-1"
}

dependencies {
    // Both loaders bundle Mixin and MixinExtras, and the mod bundles or requires the rest.
    compileOnly("net.fabricmc:sponge-mixin:0.17.3+mixin.0.8.7")
    compileOnly("io.github.llamalad7:mixinextras-common:0.5.3")
    compileOnly("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    compileOnly(libs.exposed.core)
    compileOnly(libs.exposed.dao)
    compileOnly(libs.exposed.java.time)
    compileOnly(libs.exposed.jdbc)
    compileOnly(libs.sqlite.jdbc)
    compileOnly(libs.konf.core)
    compileOnly(libs.konf.toml)

    detektPlugins(libs.detekt.formatting)
}

detekt {
    buildUponDefaultConfig = true
    autoCorrect = true
    config.setFrom(rootProject.files("detekt.yml"))
}
