import java.security.MessageDigest

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

// End-to-end tests, in their own mod so they never reach the release jar. Scenarios are shared with other loaders.
val gametest: SourceSet by sourceSets.creating {
    compileClasspath += sourceSets.main.get().output
    runtimeClasspath += sourceSets.main.get().output
    resources.srcDir(common.file("src/gametest/resources"))
}
kotlin.sourceSets.named("gametest") {
    kotlin.srcDir(common.file("src/gametest/kotlin"))
}
configurations.named("gametestImplementation") {
    extendsFrom(configurations.implementation.get())
}

neoForge {
    version = "26.1.2.126"
    addModdingDependenciesTo(gametest)
    mods {
        create(modId) {
            sourceSet(sourceSets.main.get())
        }
        create("ledger_gametest") {
            sourceSet(gametest)
        }
    }
    runs {
        create("server") {
            server()
            gameDirectory = file("runs/server")
            programArgument("--nogui")
            loadedMods.set(listOf(mods.getByName(modId)))
            systemProperty("ledger.auditMixins", "true")
            systemProperty("mixin.debug.verify", "true")
            systemProperty("mixin.debug.countInjections", "true")
        }
        // Starts a server, runs every test, and exits with the number of failed tests.
        create("gameTests") {
            type = "gameTestServer"
            sourceSet = gametest
            gameDirectory = file("runs/gametest")
            systemProperty("neoforge.enabledGameTestNamespaces", "ledger_gametest")
        }
        // The same tests with real storage mods loaded, which adds the scenarios for those mods. Slower, so it's separate.
        create("storageGameTests") {
            type = "gameTestServer"
            sourceSet = gametest
            gameDirectory = file("runs/storagetest")
            systemProperty("neoforge.enabledGameTestNamespaces", "ledger_gametest")
        }
    }
}

// Tests assert on Ledger's database, so a leftover one could make them pass falsely.
tasks.named("runGameTests") {
    doFirst {
        delete(fileTree("runs/gametest") { include("**/*") })
    }
}

// Mods for the storage tests, at the versions used by FTB Evolution 2. Downloaded rather than kept in this repository.
val downloadStorageTestMods by tasks.registering {
    val listing = file("storage-test-mods.json")
    val modsDir = file("runs/storagetest/mods")
    inputs.file(listing)
    outputs.dir(modsDir)
    doLast {
        @Suppress("UNCHECKED_CAST")
        val mods = groovy.json.JsonSlurper().parse(listing) as List<Map<String, String>>
        modsDir.mkdirs()
        val wanted = mods.map { it.getValue("file") }.toSet()
        modsDir.listFiles()?.filter { it.name !in wanted }?.forEach { it.delete() }
        mods.forEach { mod ->
            val target = modsDir.resolve(mod.getValue("file"))
            fun sha1(bytes: ByteArray) = MessageDigest.getInstance("SHA-1").digest(bytes)
                .joinToString("") { "%02x".format(it) }
            if (!target.exists() || sha1(target.readBytes()) != mod.getValue("sha1")) {
                val bytes = uri(mod.getValue("url")).toURL().openStream().use { it.readBytes() }
                check(sha1(bytes) == mod.getValue("sha1")) { "Checksum mismatch for ${mod.getValue("file")}" }
                target.writeBytes(bytes)
            }
        }
    }
}

tasks.named("runStorageGameTests") {
    dependsOn(downloadStorageTestMods)
    doFirst {
        delete(fileTree("runs/storagetest") { include("**/*"); exclude("mods/**") })
    }
}

repositories {
    maven("https://thedarkcolour.github.io/KotlinForForge/")
    maven("https://api.modrinth.com/maven") {
        content { includeGroup("maven.modrinth") }
    }
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

    // Storage mods Ledger has code for, which only runs if they're installed. Same versions as storage-test-mods.json.
    listOf(
        "maven.modrinth:ae2:26.1.13-beta",
        "maven.modrinth:refined-storage:3.2.1",
        "maven.modrinth:sophisticated-backpacks:26.1.2-3.26.18.2238",
        "maven.modrinth:sophisticated-core:26.1.2-1.5.28.2455",
    ).forEach {
        compileOnly(it)
        add("gametestCompileOnly", it)
    }

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
