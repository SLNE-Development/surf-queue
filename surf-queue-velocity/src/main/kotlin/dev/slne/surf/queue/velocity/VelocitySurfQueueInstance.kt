package dev.slne.surf.queue.velocity

import com.google.auto.service.AutoService
import dev.slne.surf.queue.common.QueueInstance
import dev.slne.surf.queue.common.queue.AbstractQueue
import dev.slne.surf.queue.common.queue.tick.QueueScheduler
import dev.slne.surf.queue.velocity.command.queueCommand
import dev.slne.surf.queue.velocity.config.VelocityQueueConfig
import dev.slne.surf.queue.velocity.listener.QueuePlayerListener
import dev.slne.surf.queue.velocity.listener.QueuePriorityListener
import dev.slne.surf.queue.velocity.queue.VelocityQueueImpl

@AutoService(QueueInstance::class)
class VelocitySurfQueueInstance : QueueInstance() {
    override val componentOwner get() = plugin.container
    override val queueScheduler: QueueScheduler =
        QueueScheduler(workerCount = 2) // More queues, but the work is primarily only displaying the position in the action bar

    @Volatile
    override var isLoaded: Boolean = false
        private set

    override suspend fun load() {
        VelocityQueueConfig.init()
        super.load()
    }

    override suspend fun enable() {
        super.enable()

        plugin.proxy.eventManager.register(plugin, QueuePlayerListener)
        QueuePriorityListener.register()
        queueCommand()
        isLoaded = true
    }

    override suspend fun disable() {
        QueuePriorityListener.unregister()
        super.disable()
    }

    override fun createQueue(serverName: String): AbstractQueue {
        return VelocityQueueImpl(serverName, queueScheduler)
    }
}