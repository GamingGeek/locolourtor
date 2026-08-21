plugins {
    id("gg.essential.defaults")
    id("gg.essential.multi-version")
}

fun Project.dependencyVersion(name: String, friendlyName: String = name, defaultValue: String? = null): String {
    return this.findProperty("dependency.$name.version") as? String
        ?: defaultValue
        ?: error("No $friendlyName version defined for ${platform.mcVersionStr} (${platform.loaderStr})")
}

group = "dev.gaminggeek"
version = "1.0.0"
base.archivesName = "locolourtor-${project.name}"

repositories {
    // Required: NeoForge dependencies can be resolved from within Fabric subprojects
    // due to the preprocessor plugin, so every subproject needs this.
    maven("https://maven.neoforged.net/releases")
}

dependencies {
    // Fabric API — provided via per-version gradle.properties
    val fabricApiVersion = findProperty("dependency.fabric-api.version") as? String
    if (fabricApiVersion != null && platform.isFabric) {
        modImplementation("net.fabricmc.fabric-api:fabric-api:$fabricApiVersion")
    }
}
