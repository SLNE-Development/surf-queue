import dev.slne.surf.api.gradle.util.slneReleases

plugins {
    id("dev.slne.surf.api.gradle.minestom")
}

surfMinestomApi {
    withCoreMinestom()
    withSurfRedis()
}

minestomPluginFile {
    main = "dev.slne.surf.queue.minestom.MinestomQueuePlugin"
    authors = providers.gradleProperty("authors").map { it.split(",") }

    pluginDependencies {
        register("surf-redis-minestom")
    }
}

dependencies {
    api(project(":surf-queue-core-client"))
}

publishing {
    repositories {
        slneReleases()
    }
}
