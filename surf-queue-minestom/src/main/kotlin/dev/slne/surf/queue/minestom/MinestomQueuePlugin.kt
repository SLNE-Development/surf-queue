package dev.slne.surf.queue.minestom

import dev.slne.surf.api.minestom.plugin.SurfMinestomPlugin
import dev.slne.surf.queue.common.QueueInstance
import dev.slne.surf.queue.minestom.command.queueCommand

object MinestomQueuePlugin : SurfMinestomPlugin() {
    override suspend fun onLoad() {
        QueueInstance.get().load()
    }

    override suspend fun onEnable() {
        QueueInstance.get().enable()
        queueCommand()
    }

    override suspend fun onDisable() {
        QueueInstance.get().disable()
    }
}
