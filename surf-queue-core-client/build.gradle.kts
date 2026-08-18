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
