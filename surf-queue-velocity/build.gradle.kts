plugins {
    id("dev.slne.surf.api.gradle.velocity")
}

surfVelocityApi {
    withSurfRedis()
    withCoreVelocity()
}

sourceSets.test {
    compileClasspath += sourceSets.main.get().compileClasspath
    runtimeClasspath += sourceSets.main.get().compileClasspath
}

dependencies {
    implementation(project(":surf-queue-common"))

    compileOnly("dev.slne.surf.settings:surf-settings-api:+")

    compileOnly("io.github.toxicity188:BetterHud-standard-api:1.14.1") //Standard api
    compileOnly("io.github.toxicity188:BetterHud-velocity-api:1.14.1") //Platform api
}

velocityPluginFile {
    main = "dev.slne.surf.queue.velocity.VelocityMain"
    pluginDependencies {
        register("luckperms") {
            optional = false
        }

        register("surf-settings-velocity") {
            optional = true
        }
    }
}