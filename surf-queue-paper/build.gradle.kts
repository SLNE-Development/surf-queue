plugins {
    id("dev.slne.surf.surfapi.gradle.paper-plugin")
}

surfPaperPluginApi {
    mainClass("dev.slne.surf.queue.paper.PaperMain")
    authors.addAll(providers.gradleProperty("authors").map { it.split(",") })
}

dependencies {
    api(project(":surf-queue-common"))
}