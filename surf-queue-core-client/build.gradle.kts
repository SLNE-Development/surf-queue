import dev.slne.surf.api.gradle.util.slneReleases

plugins {
    id("dev.slne.surf.api.gradle.core")
}

surfCoreApi {
    withSurfRedis()
    withCoreCommon()
}

dependencies {
    api(project(":surf-queue-common"))
}

publishing {
    repositories {
        slneReleases()
    }
}