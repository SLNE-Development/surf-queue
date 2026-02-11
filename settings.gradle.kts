plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}


rootProject.name = "surf-queue"
include("surf-queue-common")
include("surf-queue-velocity")
include("surf-queue-paper")
include("surf-queue-api")