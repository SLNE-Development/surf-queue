package dev.slne.surf.queue.velocity

import com.google.auto.service.AutoService
import dev.slne.surf.queue.common.QueueInstance
import dev.slne.surf.queue.common.queue.AbstractQueue
import dev.slne.surf.queue.common.queue.QueueTicker
import dev.slne.surf.queue.velocity.command.queueCommand
import dev.slne.surf.queue.velocity.listener.QueuePlayerListener
import dev.slne.surf.queue.velocity.queue.VelocityQueueImpl

/**
 * Velocity-specific [QueueInstance] implementation.
 *
 * Registers the [QueuePlayerListener], registers commands, and starts the
 * [QueueTicker] on enable. Creates [VelocityQueueImpl] instances for each
 * target server.
 */
@AutoService(QueueInstance::class)
class VelocitySurfQueueInstance : QueueInstance() {
    override val componentOwner get() = plugin.container

    override suspend fun enable() {
        super.enable()

        plugin.proxy.eventManager.register(plugin, QueuePlayerListener)
        queueCommand()
        QueueTicker.start()
    }

    override fun createQueue(serverName: String): AbstractQueue {
        return VelocityQueueImpl(serverName)
    }
}