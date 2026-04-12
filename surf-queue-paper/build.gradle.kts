import dev.slne.surf.api.gradle.util.registerRequired
import dev.slne.surf.api.gradle.util.registerSoft

plugins {
    id("dev.slne.surf.api.gradle.paper-plugin")
}


surfPaperPluginApi {
    mainClass("dev.slne.surf.queue.paper.PaperMain")
    withSurfRedis()
    withCorePaper()
    authors.addAll(providers.gradleProperty("authors").map { it.split(",") })
    serverDependencies {
        registerRequired("LuckPerms")
        registerSoft("PolarLoader")
    }
}

dependencies {
    api(project(":surf-queue-common"))
    compileOnly(libs.polar)
}