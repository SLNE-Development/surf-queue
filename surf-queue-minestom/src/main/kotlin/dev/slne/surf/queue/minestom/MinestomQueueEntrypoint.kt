package dev.slne.surf.queue.minestom

import com.google.inject.Inject
import com.google.inject.Singleton
import dev.slne.minestom.lobby.api.plugin.MinestomPluginEntrypoint
import dev.slne.minestom.lobby.api.plugin.annotation.DataDirectory
import dev.slne.surf.queue.common.QueueInstance
import java.nio.file.Path

@Singleton
class MinestomQueueEntrypoint @Inject constructor(
    @DataDirectory path: Path
) : MinestomPluginEntrypoint {

    init {
        dataPath = path
    }

    override suspend fun start() {
        QueueInstance.get().load()
        QueueInstance.get().enable()
    }

    override suspend fun stop() {
        QueueInstance.get().disable()
    }

    companion object {
        lateinit var dataPath: Path
    }
}
