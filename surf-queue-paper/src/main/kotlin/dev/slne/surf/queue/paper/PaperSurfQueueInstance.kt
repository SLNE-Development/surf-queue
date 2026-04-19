package dev.slne.surf.queue.paper

import com.google.auto.service.AutoService
import dev.slne.surf.api.paper.event.register
import dev.slne.surf.core.api.common.server.SurfServer
import dev.slne.surf.queue.common.QueueInstance
import dev.slne.surf.queue.common.queue.AbstractQueue
import dev.slne.surf.queue.common.queue.tick.QueueScheduler
import dev.slne.surf.queue.paper.commands.queueCommand
import dev.slne.surf.queue.paper.config.SurfQueueConfig
import dev.slne.surf.queue.paper.hook.startup.QueueStartHook
import dev.slne.surf.queue.paper.listener.PlayerKickedDueToFullServerListener
import dev.slne.surf.queue.paper.metrics.QueueMetricsLogger
import dev.slne.surf.queue.paper.queue.PaperOwnedQueueImpl
import dev.slne.surf.queue.paper.queue.PaperQueueImpl

@AutoService(QueueInstance::class)
class PaperSurfQueueInstance : QueueInstance() {
    override val componentOwner get() = plugin
    override val queueScheduler =
        QueueScheduler(workerCount = 1) // Only one tickable queue max, so 1 worker is enough

    override var isLoaded: Boolean = false
        private set

    override suspend fun load() {
        SurfQueueConfig.init()
        super.load()
        QueueStartHook.get().onServerReady {
            isLoaded = true
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
        return if (serverName == SurfServer.current().name) {
            PaperOwnedQueueImpl(serverName, queueScheduler)
        } else {
            PaperQueueImpl(serverName)
        }
    }
}