package dev.slne.surf.queue.api.service

import dev.slne.surf.queue.api.InternalSurfQueueApi
import dev.slne.surf.queue.api.SurfQueue
import dev.slne.surf.surfapi.core.api.util.requiredService

@InternalSurfQueueApi
interface SurfQueueService {
    fun get(serverName: String): SurfQueue

    companion object {
        val instance = requiredService<SurfQueueService>()
    }
}