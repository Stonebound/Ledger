plugins {
    id("org.jetbrains.kotlin.jvm")
    id("net.neoforged.moddev")
    id("dev.detekt")
}

val modId: String by project
val modName: String by project
val modVersion: String by project
val mavenGroup: String by project

base.archivesName.set("$modId-neoforge")
version = modVersion
group = mavenGroup

// Shared sources are compiled as part of this module.
val common = project(":common")
sourceSets.main {
    java.srcDir(common.file("src/main/java"))
    resources.srcDir(common.file("src/main/resources"))
}
kotlin.sourceSets.main {
    kotlin.srcDir(common.file("src/main/kotlin"))
}

neoForge {
    version = "26.1.2.126"
    runs {
        create("server") {
            server()
            gameDirectory = file("runs/server")
            programArgument("--nogui")
            systemProperty("ledger.auditMixins", "true")
            systemProperty("mixin.debug.verify", "true")
            systemProperty("mixin.debug.countInjections", "true")
        }
    }
    mods {
        create(modId) {
            sourceSet(sourceSets.main.get())
        }
    }
}

repositories {
    maven("https://thedarkcolour.github.io/KotlinForForge/")
}

// Runtime scanning library that Konf pulls in; jarJar rejects it and Ledger's Konf calls don't need it.
listOf("runtimeClasspath", "compileClasspath").forEach { name ->
    configurations.named(name) {
        exclude(group = "org.reflections")
    }
}

dependencies {
    implementation("thedarkcolour:kotlinforforge-neoforge:6.4.0")

    listOf(
        libs.exposed.core,
        libs.exposed.dao,
        libs.exposed.java.time,
        libs.exposed.jdbc,
        libs.sqlite.jdbc,
        libs.konf.core,
        libs.konf.toml,
    ).forEach {
        implementation(it)
        add("jarJar", it)
    }

    // jarJar does not bundle dependencies of dependencies, so Exposed's and Konf's are listed explicitly.
    // Anything else in their trees is provided by Minecraft or Kotlin for Forge. Missing ones only fail in packaged jars.
    listOf(
        "org.jetbrains.kotlinx:kotlinx-datetime-jvm:0.7.1-0.6.x-compat",
        "com.fasterxml.jackson.core:jackson-annotations:2.12.2",
        "com.fasterxml.jackson.core:jackson-core:2.12.2",
        "com.fasterxml.jackson.core:jackson-databind:2.12.2",
        "com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.12.2",
        "com.fasterxml.jackson.module:jackson-module-kotlin:2.12.2",
        "org.apache.commons:commons-text:1.9",
    ).forEach { add("jarJar", it) }
    add("jarJar", project(":toml4j-module"))

    detektPlugins(libs.detekt.formatting)
}

tasks {
    processResources {
        val properties = mapOf(
            "id" to modId,
            "version" to modVersion,
            "name" to modName,
            "minecraft" to libs.versions.minecraft.get(),
        )
        inputs.properties(properties)
        filesMatching("META-INF/neoforge.mods.toml") {
            expand(properties)
        }
    }

    jar {
        from(rootProject.file("LICENSE.md"))
    }

    // Lets console commands such as "stop" reach a server started through Gradle.
    withType<JavaExec>().configureEach {
        standardInput = System.`in`
    }
}

detekt {
    buildUponDefaultConfig = true
    autoCorrect = true
    config.setFrom(rootProject.files("detekt.yml"))
}
