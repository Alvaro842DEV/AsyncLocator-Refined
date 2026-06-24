plugins {
    idea
    java
    `maven-publish`
    id("org.spongepowered.gradle.vanilla")
}

val mod_name: String by project
val minecraft_version: String by project
val mod_id: String by project

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
    val props = project.properties
    filesMatching("pack.mcmeta") {
        expand(props)
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
