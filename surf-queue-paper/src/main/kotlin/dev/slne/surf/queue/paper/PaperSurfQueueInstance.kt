package dev.slne.surf.queue.paper

import com.google.auto.service.AutoService
import dev.slne.surf.queue.common.SurfQueueInstance
import dev.slne.surf.queue.common.queue.AbstractSurfQueue
import dev.slne.surf.queue.paper.config.SurfQueueConfig
import dev.slne.surf.queue.paper.hook.startup.QueueStartHook
import dev.slne.surf.queue.paper.listener.PlayerKickedDueToFullServerListener
import dev.slne.surf.queue.paper.queue.PaperQueueTickTask
import dev.slne.surf.queue.paper.queue.PaperSurfQueue
import dev.slne.surf.surfapi.bukkit.api.event.register

@AutoService(SurfQueueInstance::class)
class PaperSurfQueueInstance : SurfQueueInstance() {
    override val componentOwner get() = plugin

    override suspend fun load() {
        SurfQueueConfig.init()
        super.load()
        QueueStartHook.get().onServerReady {
            PaperQueueTickTask.start()
        }
    }

    override suspend fun enable() {
        super.enable()
        PlayerKickedDueToFullServerListener.register()
    }

    override suspend fun disable() {
        PaperQueueTickTask.shutdown()
        super.disable()
    }

    override fun createQueue(serverName: String): AbstractSurfQueue {
        return PaperSurfQueue(serverName)
    }
}