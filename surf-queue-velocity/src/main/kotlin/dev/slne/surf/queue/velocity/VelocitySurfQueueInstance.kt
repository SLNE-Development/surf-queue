package dev.slne.surf.queue.velocity

import com.google.auto.service.AutoService
import dev.slne.surf.queue.common.SurfQueueInstance
import dev.slne.surf.queue.common.queue.AbstractSurfQueue
import dev.slne.surf.queue.velocity.command.queueCommand
import dev.slne.surf.queue.velocity.listener.QueuePlayerListener
import dev.slne.surf.queue.velocity.queue.QueueTickTask
import dev.slne.surf.queue.velocity.queue.VelocitySurfQueue

@AutoService(SurfQueueInstance::class)
class VelocitySurfQueueInstance : SurfQueueInstance() {

    override suspend fun enable() {
        super.enable()

        plugin.proxy.eventManager.register(plugin, QueuePlayerListener)
        QueueTickTask.startTransferring()
        queueCommand()
    }

    override suspend fun disable() {
        super.disable()

        QueueTickTask.shutdown()
    }

    override fun createQueue(serverName: String): AbstractSurfQueue {
        return VelocitySurfQueue(serverName)
    }
}