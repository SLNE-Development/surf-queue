package dev.slne.surf.queue.velocity

import com.google.auto.service.AutoService
import dev.slne.surf.queue.common.SurfQueueInstance
import dev.slne.surf.queue.velocity.listener.QueuePlayerListener
import dev.slne.surf.queue.velocity.transfer.TransferTask

@AutoService(SurfQueueInstance::class)
class VelocitySurfQueueInstance : SurfQueueInstance() {

    override suspend fun enable() {
        super.enable()

        plugin.proxy.eventManager.register(plugin, QueuePlayerListener)
        TransferTask.startTransferring()
    }

    override suspend fun disable() {
        super.disable()

        TransferTask.shutdown()
    }
}