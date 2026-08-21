pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        maven("https://repo.essential.gg/repository/maven-public")
        maven("https://maven.architectury.dev")
        maven("https://maven.fabricmc.net")
        maven("https://maven.neoforged.net/releases")
        maven("https://maven.minecraftforge.net")
    }

    plugins {
        val egtVersion = "0.7.2"
        id("gg.essential.multi-version.root") version egtVersion
    }
}

rootProject.name = "locolourtor"

// We use the `build.gradle.kts` file for all the subprojects, so we point the
// root project to root.gradle.kts where the preprocessor configuration lives.
rootProject.buildFileName = "root.gradle.kts"

listOf(
    "1.21.11-fabric",
    "1.21.11-neoforge",
    "26.1-fabric",
    "26.1-neoforge",
    "26.1.1-fabric",
    "26.1.1-neoforge",
    "26.1.2-fabric",
    "26.1.2-neoforge",
    "26.2-fabric",
    "26.2-neoforge",
).forEach { version ->
    include(":$version")
    project(":$version").apply {
        // Subproject folder where Gradle properties and build artifacts live
        projectDir = file("versions/$version")

        // All subprojects share the root build.gradle.kts
        buildFileName = "../../build.gradle.kts"
    }
}
