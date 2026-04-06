package dev.slne.surf.queue.paper

import com.google.auto.service.AutoService
import dev.slne.surf.queue.common.SurfQueueInstance
import dev.slne.surf.queue.common.queue.AbstractSurfQueue
import dev.slne.surf.queue.paper.hook.startup.QueueStartHook
import dev.slne.surf.queue.paper.queue.PaperQueueTickTask
import dev.slne.surf.queue.paper.queue.PaperSurfQueue
import dev.slne.surf.surfapi.core.api.util.logger
import org.bukkit.Bukkit

@AutoService(SurfQueueInstance::class)
class PaperSurfQueueInstance : SurfQueueInstance() {
    override val componentOwner get() = plugin

    companion object {
        private val log = logger()
    }

    override suspend fun load() {
        super.load()
        QueueStartHook.get().onServerReady {

        }
    }

    override suspend fun enable() {
        super.enable()

        // getServerName() is deprecated in Paper but is the standard way to obtain
        // the server name configured in server.properties (server-name=...).
        // This must match the server name registered in the Velocity proxy config.
        @Suppress("DEPRECATION")
        val serverName = Bukkit.getServerName()
        log.atInfo().log("Starting queue tick task for server: %s", serverName)
        PaperQueueTickTask.start(serverName)
    }

    override suspend fun disable() {
        PaperQueueTickTask.shutdown()
        super.disable()
    }

    override fun createQueue(serverName: String): AbstractSurfQueue {
        return PaperSurfQueue(serverName)
    }
}