import dev.slne.surf.api.gradle.util.slneReleases

plugins {
    id("dev.slne.surf.api.gradle.minestom")
}

surfMinestomApi {
    withCoreMinestom()
    withSurfRedis()
}

dependencies {
    api(project(":surf-queue-core-client"))
}

publishing {
    repositories {
        slneReleases()
    }
}
