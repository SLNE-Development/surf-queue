plugins {
    id("dev.slne.surf.api.gradle.core")
}

surfCoreApi {
    withSurfRedis()
    withCoreCommon()
}

dependencies {
    api(project(":surf-queue-api"))
    compileOnlyApi("net.luckperms:api:5.4")
}