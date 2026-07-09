plugins {
    java
    idea
    `maven-publish`
    id("fabric-loom")
}

val mod_name = providers.gradleProperty("mod_name").get()
val minecraft_version = providers.gradleProperty("minecraft_version").get()
val mod_id = providers.gradleProperty("mod_id").get()
val fabric_loader_version = providers.gradleProperty("fabric_loader_version").get()
val fabric_version = providers.gradleProperty("fabric_version").get()

base {
    archivesName.set("$mod_name-fabric-$minecraft_version")
}

val commonMain = project(":Common").extensions.getByType<SourceSetContainer>()["main"]

dependencies {
    minecraft("com.mojang:minecraft:$minecraft_version")
    mappings(loom.officialMojangMappings())
    modImplementation("net.fabricmc:fabric-loader:$fabric_loader_version")
    modImplementation("net.fabricmc.fabric-api:fabric-api:$fabric_version")
}

loom {
    val accessWidener = project(":Common").file("src/main/resources/$mod_id.accesswidener")
    if (accessWidener.exists()) {
        accessWidenerPath.set(accessWidener)
    }

    runs {
        named("client") {
            client()
            setConfigName("Fabric Client")
            ideConfigGenerated(true)
            runDir("run")
        }
        named("server") {
            server()
            setConfigName("Fabric Server")
            ideConfigGenerated(true)
            runDir("run")
        }
        // Run with: ./gradlew :Fabric:runGametest
        @Suppress("DEPRECATION")
        create("gametest") {
            server()
            vmArg("-Dfabric-api.gametest")
            vmArg(
                "-Dfabric-api.gametest.report-file=" +
                    layout.buildDirectory.file("gametest/junit.xml").get().asFile.absolutePath
            )
            runDir("build/gametest")
        }
    }
}

tasks.named<ProcessResources>("processResources") {
    from(commonMain.resources)

    val modId = mod_id
    val modName = mod_name
    val modVersion = project.version
    val minecraftVersion = minecraft_version

    filesMatching("fabric.mod.json") {
        expand(
            mapOf(
                "version" to modVersion,
                "minecraft_version" to minecraftVersion,
                "mod_id" to modId,
                "mod_name" to modName,
            )
        )
    }
}

tasks.withType<JavaCompile>().configureEach {
    source(commonMain.allSource)
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            artifactId = base.archivesName.get()
            from(components["java"])
        }
    }
    repositories {
        maven {
            setUrl("file://" + System.getenv("local_maven"))
        }
    }
}

// Configure all JavaExec tasks within this subproject
tasks.withType<JavaExec>().configureEach {
    jvmArgs("--add-opens=java.base/java.lang=ALL-UNNAMED")
}
