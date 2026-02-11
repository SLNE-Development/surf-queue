plugins {
    id("dev.slne.surf.surfapi.gradle.core")
}

surfCoreApi {
    withSurfRedis()
    withCoreCommon()
}