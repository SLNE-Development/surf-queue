package dev.slne.surf.queue.minestom

import com.google.auto.service.AutoService
import dev.slne.minestom.lobby.api.plugin.MinestomPlugin
import dev.slne.minestom.lobby.api.plugin.annotation.MinestomPluginMeta
import dev.slne.surf.queue.minestom.command.QueueCommandRegistrar

@AutoService(MinestomPlugin::class)
@MinestomPluginMeta(
    "surf-queue-minestom",
    dependsOn = [
        "surf-api-minestom",
        "surf-redis-minestom",
        "surf-core-minestom"
    ]
)
class MinestomQueuePlugin : MinestomPlugin(MinestomQueueEntrypoint::class.java) {
    override fun configurePlugin() {
        bindCommandRegistrar<QueueCommandRegistrar>()
    }
}
