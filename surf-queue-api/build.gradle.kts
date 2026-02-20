import dev.slne.surf.surfapi.gradle.util.slneReleases

plugins {
    id("dev.slne.surf.surfapi.gradle.core")
}

surfCoreApi {
    withCoreCommon()
}

publishing {
    repositories {
        slneReleases()
    }
}