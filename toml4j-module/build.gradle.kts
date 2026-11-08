// toml4j publishes no Java module name, which NeoForge's jarJar requires.
// This repackages it unchanged with one so it can sit in the same classloader layer as Konf.
plugins {
    java
}

group = "com.github.quiltservertools.ledger"
version = "0.7.2"

val upstream: Configuration by configurations.creating {
    isTransitive = false
}

dependencies {
    upstream("com.moandjiezana.toml:toml4j:0.7.2")
}

tasks.jar {
    from(upstream.map { zipTree(it) }) {
        exclude("META-INF/**")
    }
    manifest {
        attributes("Automatic-Module-Name" to "com.moandjiezana.toml")
    }
}
