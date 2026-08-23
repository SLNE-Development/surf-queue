package dev.slne.surf.queue.minestom

import com.google.auto.service.AutoService
import dev.slne.surf.queue.client.ClientQueueInstance
import dev.slne.surf.queue.common.QueueInstance
import java.nio.file.Path

@AutoService(QueueInstance::class)
class MinestomSurfQueueInstance : ClientQueueInstance() {
    override val dataPath: Path get() = MinestomQueueEntrypoint.dataPath

    @Volatile
    override var isLoaded: Boolean = false
        private set

    override suspend fun enable() {
        super.enable()
        isLoaded = true
    }
}
