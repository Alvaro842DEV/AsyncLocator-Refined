plugins {
    idea
    java
    `maven-publish`
    id("org.spongepowered.gradle.vanilla")
}

val mod_name = providers.gradleProperty("mod_name").get()
val minecraft_version = providers.gradleProperty("minecraft_version").get()
val mod_id = providers.gradleProperty("mod_id").get()

base {
    archivesName.set("$mod_name-common-$minecraft_version")
}

minecraft {
    version(minecraft_version)
    val accessWidener = file("src/main/resources/$mod_id.accesswidener")
    if (accessWidener.exists()) {
        accessWideners(accessWidener)
    }
}

dependencies {
    compileOnly("org.jetbrains:annotations:26.0.2")
    compileOnly("org.spongepowered:mixin:0.8.7")
}

tasks.named<ProcessResources>("processResources") {
    val expandProps = mapOf(
        "version" to project.version.toString(),
        "minecraft_version" to minecraft_version,
        "mod_id" to mod_id,
        "mod_name" to mod_name,
    )
    inputs.properties(expandProps)
    filesMatching("pack.mcmeta") {
        expand(expandProps)
    }
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
