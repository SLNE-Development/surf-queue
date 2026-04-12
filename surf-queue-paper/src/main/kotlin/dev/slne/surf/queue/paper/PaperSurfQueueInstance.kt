package dev.slne.surf.queue.paper

import com.google.auto.service.AutoService
import dev.slne.surf.queue.common.QueueInstance
import dev.slne.surf.queue.common.queue.AbstractQueue
import dev.slne.surf.queue.common.queue.QueueTicker
import dev.slne.surf.queue.paper.commands.queueCommand
import dev.slne.surf.queue.paper.config.SurfQueueConfig
import dev.slne.surf.queue.paper.hook.startup.QueueStartHook
import dev.slne.surf.queue.paper.listener.PlayerKickedDueToFullServerListener
import dev.slne.surf.queue.paper.metrics.QueueMetricsLogger
import dev.slne.surf.queue.paper.queue.PaperQueueImpl
import dev.slne.surf.api.paper.event.register

@AutoService(QueueInstance::class)
class PaperSurfQueueInstance : QueueInstance() {
    override val componentOwner get() = plugin

    override suspend fun load() {
        SurfQueueConfig.init()
        super.load()
        QueueStartHook.get().onServerReady {
            QueueTicker.start()
        }
    }

    override suspend fun enable() {
        super.enable()
        PlayerKickedDueToFullServerListener.register()
        queueCommand()
    }

    override suspend fun disable() {
        super.disable()
        QueueMetricsLogger.stop()
    }

    override fun createQueue(serverName: String): AbstractQueue {
        return PaperQueueImpl(serverName)
    }
}