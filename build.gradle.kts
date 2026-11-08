plugins {
    alias(libs.plugins.kotlin) apply false
    alias(libs.plugins.loom) apply false
    alias(libs.plugins.moddev) apply false
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.git.hooks)
}

gitHooks {
    setHooks(
        mapOf("pre-commit" to "detekt")
    )
}

subprojects {
    repositories {
        maven("https://maven.fabricmc.net/")
        maven("https://maven.nucleoid.xyz/")
        maven("https://jitpack.io")
        maven("https://oss.sonatype.org/content/repositories/snapshots")
        mavenCentral()
    }

    pluginManager.withPlugin("java") {
        extensions.configure<JavaPluginExtension> {
            toolchain.languageVersion.set(JavaLanguageVersion.of(25))
        }
    }

    pluginManager.withPlugin("org.jetbrains.kotlin.jvm") {
        extensions.configure<org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension> {
            compilerOptions.jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_25)
        }
    }
}
