import com.diffplug.gradle.spotless.SpotlessExtension
import java.text.SimpleDateFormat
import java.util.Date

plugins {
    id("fabric-loom") version "1.17-SNAPSHOT" apply false
    id("net.neoforged.moddev") version "2.0.141" apply false
    id("org.spongepowered.gradle.vanilla") version "0.2.2" apply false
    id("org.spongepowered.mixin") version "0.7.38" apply false
    id("com.diffplug.spotless") version "8.7.0" apply false
}

subprojects {
    apply(plugin = "java")
    apply(plugin = "com.diffplug.spotless")

    val mod_name = providers.gradleProperty("mod_name").get()
    val mod_author = providers.gradleProperty("mod_author").get()
    val minecraft_version = providers.gradleProperty("minecraft_version").get()

    configure<JavaPluginExtension> {
        toolchain.languageVersion.set(JavaLanguageVersion.of(21))
        withSourcesJar()
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
        val mod_id = providers.gradleProperty("mod_id").get()
        val mod_credits = providers.gradleProperty("mod_credits").get()
        val expandProps = mapOf(
            "version" to project.version.toString(),
            "minecraft_version" to minecraft_version,
            "mod_id" to mod_id,
            "mod_name" to mod_name,
            "mod_author" to mod_author,
            "mod_credits" to mod_credits,
        )
        inputs.properties(expandProps)
        filesMatching(listOf("pack.mcmeta", "fabric.mod.json", "neoforge.mods.toml", "*.mixins.json")) {
            expand(expandProps)
        }
    }

    // Disables Gradle's custom module metadata from being published to maven. The
    // metadata includes mapped dependencies which are not reasonably consumable by
    // other mod developers.
    tasks.withType<GenerateModuleMetadata>().configureEach {
        enabled = false
    }
    tasks.withType<Test>().configureEach {
        failOnNoDiscoveredTests = false
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
