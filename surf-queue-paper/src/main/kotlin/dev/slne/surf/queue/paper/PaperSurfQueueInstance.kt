package dev.slne.surf.queue.paper

import com.google.auto.service.AutoService
import dev.slne.surf.api.paper.event.register
import dev.slne.surf.queue.client.ClientQueueInstance
import dev.slne.surf.queue.common.QueueInstance
import dev.slne.surf.queue.paper.commands.queueCommand
import dev.slne.surf.queue.paper.hook.startup.QueueStartHook
import dev.slne.surf.queue.paper.listener.PlayerKickedDueToFullServerListener

@AutoService(QueueInstance::class)
class PaperSurfQueueInstance : ClientQueueInstance() {
    override val componentOwner get() = plugin
    override val dataPath get() = plugin.dataPath

    @Volatile
    override var isLoaded: Boolean = false
        private set

    override suspend fun load() {
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
}
