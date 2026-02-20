package dev.slne.surf.queue.paper

import com.google.auto.service.AutoService
import dev.slne.surf.queue.common.SurfQueueInstance
import dev.slne.surf.queue.common.queue.AbstractSurfQueue
import dev.slne.surf.queue.paper.queue.PaperSurfQueue

@AutoService(SurfQueueInstance::class)
class PaperSurfQueueInstance : SurfQueueInstance() {
    override val componentOwner get() = dev.slne.surf.queue.paper.plugin

    override fun createQueue(serverName: String): AbstractSurfQueue {
        return PaperSurfQueue(serverName)
    }
}