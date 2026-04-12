pluginManagement {
    repositories {
        gradlePluginPortal()
        maven("https://reposilite.slne.dev/releases")
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
    id("dev.slne.surf.api.gradle.settings") version "+"
}



rootProject.name = "surf-queue"
include("surf-queue-common")
include("surf-queue-velocity")
include("surf-queue-paper")
include("surf-queue-api")