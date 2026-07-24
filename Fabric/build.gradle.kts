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
    modImplementation("net.fabricmc.fabric-api:fabric-api:$fabric_version") {
        isTransitive = false
    }
    modImplementation(fabricApi.module("fabric-lifecycle-events-v1", fabric_version))
    modImplementation(fabricApi.module("fabric-gametest-api-v1", fabric_version))
    testImplementation(platform("org.junit:junit-bom:5.13.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

loom {
    val accessWidener = project(":Common").file("src/main/resources/$mod_id.accesswidener")
    if (accessWidener.exists()) {
        accessWidenerPath.set(accessWidener)
    }

    runs {
        named("client") {
            client()
            displayName.set("Fabric Client")
            generateRunConfig.set(true)
            runDirectory.set(layout.projectDirectory.dir("run"))
        }
        named("server") {
            server()
            displayName.set("Fabric Server")
            generateRunConfig.set(true)
            runDirectory.set(layout.projectDirectory.dir("run"))
        }
        // Run with: ./gradlew :Fabric:runGametest
        create("gametest") {
            server()
            jvmArguments.add("-Dfabric-api.gametest")
            jvmArguments.add(
                "-Dfabric-api.gametest.report-file=" +
                    layout.buildDirectory.file("gametest/junit.xml").get().asFile.absolutePath
            )
            runDirectory.set(layout.projectDirectory.dir("build/gametest"))
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

tasks.test {
    useJUnitPlatform()
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
