import com.diffplug.gradle.spotless.SpotlessExtension
import java.text.SimpleDateFormat
import java.util.Date

plugins {
    id("fabric-loom") version "1.17-SNAPSHOT" apply false
    id("net.neoforged.moddev") version "2.0.141" apply false
    id("org.spongepowered.gradle.vanilla") version "0.2.2" apply false
    id("com.diffplug.spotless") version "8.7.0" apply false
}

subprojects {
    apply(plugin = "java")
    apply(plugin = "com.diffplug.spotless")

    val mod_name: String by project
    val mod_author: String by project
    val minecraft_version: String by project

    configure<JavaPluginExtension> {
        toolchain.languageVersion.set(JavaLanguageVersion.of(21))
    }

    tasks.named<Jar>("jar") {
        from(rootProject.file("LICENSE")) {
            rename { "${it}_$mod_name" }
        }
        val archiveVersionProvider = archiveVersion
        manifest {
            attributes(
                "Specification-Title" to mod_name,
                "Specification-Vendor" to mod_author,
                "Specification-Version" to archiveVersionProvider,
                "Implementation-Title" to project.name,
                "Implementation-Version" to archiveVersionProvider,
                "Implementation-Vendor" to mod_author,
                "Implementation-Timestamp" to
                    providers.provider { SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ").format(Date()) },
                "Built-On-Java" to
                    providers.provider {
                        "${System.getProperty("java.vm.version")} (${System.getProperty("java.vm.vendor")})"
                    },
                "Built-On-Minecraft" to minecraft_version,
            )
        }
    }

    repositories {
        mavenCentral()
        maven {
            name = "Sponge / Mixin"
            url = uri("https://repo.spongepowered.org/repository/maven-public/")
        }
        maven {
            name = "BlameJared Maven (JEI / CraftTweaker / Bookshelf)"
            url = uri("https://maven.blamejared.com")
        }
    }

    tasks.withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        options.release.set(21)
        // Enable Xlint all except serial and processing
        options.compilerArgs.add("-Xlint:all,-serial,-processing")
    }

    tasks.named<ProcessResources>("processResources") {
        val props = project.properties
        filesMatching(listOf("pack.mcmeta", "fabric.mod.json", "neoforge.mods.toml", "*.mixins.json")) {
            expand(props)
        }
    }

    // Disables Gradle's custom module metadata from being published to maven. The
    // metadata includes mapped dependencies which are not reasonably consumable by
    // other mod developers.
    tasks.withType<GenerateModuleMetadata>().configureEach {
        enabled = false
    }

    configure<SpotlessExtension> {
        java {
            target("src/**/*.java")
            palantirJavaFormat() // 4 spaces, no tabs, 100 characters per line
            removeUnusedImports()
            trimTrailingWhitespace()
            endWithNewline()
        }
    }

    tasks.named("check") {
        dependsOn("spotlessCheck")
    }
}
