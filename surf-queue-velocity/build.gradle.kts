plugins {
    id("dev.slne.surf.api.gradle.velocity")
}

surfVelocityApi {
    withSurfRedis()
    withCoreVelocity()
}

dependencies {
    implementation(project(":surf-queue-common"))

    compileOnly("io.github.toxicity188:BetterHud-standard-api:1.14.1") //Standard api
    compileOnly("io.github.toxicity188:BetterHud-velocity-api:1.14.1") //Platform api
}

velocityPluginFile {
    main = "dev.slne.surf.queue.velocity.VelocityMain"
    pluginDependencies {
        register("luckperms") {
            optional = false
        }
    }
}