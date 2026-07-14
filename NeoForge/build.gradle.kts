plugins {
    idea
    `maven-publish`
    id("net.neoforged.moddev")
}

val mod_name = providers.gradleProperty("mod_name").get()
val minecraft_version = providers.gradleProperty("minecraft_version").get()
val mod_id = providers.gradleProperty("mod_id").get()
val neoforge_version = providers.gradleProperty("neoforge_version").get()

base {
    archivesName.set("$mod_name-neoforge-$minecraft_version")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

val commonMain = project(":Common").extensions.getByType<SourceSetContainer>()["main"]

neoForge {
    version = neoforge_version

    // Automatically enable NeoForge AccessTransformers if the file exists
    val atFile = file("src/main/resources/META-INF/accesstransformer.cfg")
    if (atFile.exists()) {
        accessTransformers.from(atFile)
    }

    val mainMod = mods.create(mod_id) {
        sourceSet(sourceSets["main"])
    }

    runs {
        create("client") {
            client()
            gameDirectory.set(file("run/client"))
            programArguments.addAll("--username", "Dev")

            loadedMods.add(mainMod)
        }

        create("server") {
            server()
            gameDirectory.set(file("run/server"))
            programArguments.addAll("--nogui")

            loadedMods.add(mainMod)
        }

        // Run with: ./gradlew :NeoForge:runGametest
        create("gametest") {
            type = "gameTestServer"
            gameDirectory.set(file("run/gametest"))

            loadedMods.add(mainMod)
        }

        create("data") {
            clientData()
            gameDirectory.set(file("run/data"))
            programArguments.addAll(
                "--mod", mod_id, "--all",
                "--output", file("src/generated/resources/").absolutePath,
                "--existing", file("src/main/resources/").absolutePath,
            )

            loadedMods.add(mainMod)
        }
    }
}

dependencies {
    // We compile sources directly
}

tasks.withType<Test>().configureEach {
    failOnNoDiscoveredTests = false
}

tasks.named<ProcessResources>("processResources") {
    from(commonMain.resources)

    val modId = mod_id
    val modName = mod_name
    val modVersion = project.version
    val minecraftVersion = minecraft_version

    filesMatching(listOf("META-INF/neoforge.mods.toml", "pack.mcmeta")) {
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

tasks.named<JavaCompile>("compileJava") {
    source(commonMain.allSource)
    options.encoding = "UTF-8"
    options.release.set(21)
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
