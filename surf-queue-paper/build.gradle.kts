import dev.slne.surf.surfapi.gradle.util.registerRequired

plugins {
    id("dev.slne.surf.surfapi.gradle.paper-plugin")
}


surfPaperPluginApi {
    mainClass("dev.slne.surf.queue.paper.PaperMain")
    withSurfRedis()
    authors.addAll(providers.gradleProperty("authors").map { it.split(",") })
    serverDependencies {
        registerRequired("LuckPerms")
    }
}

dependencies {
    api(project(":surf-queue-common"))
}