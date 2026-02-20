plugins {
    id("dev.slne.surf.surfapi.gradle.velocity")
}

surfVelocityApi {
    withSurfRedis()
    withCoreVelocity()
}

dependencies {
    implementation(project(":surf-queue-common"))
}

velocityPluginFile {
    main = "dev.slne.surf.queue.velocity.VelocityMain"
    pluginDependencies {
        register("luckperms") {
            optional = false
        }
    }
}