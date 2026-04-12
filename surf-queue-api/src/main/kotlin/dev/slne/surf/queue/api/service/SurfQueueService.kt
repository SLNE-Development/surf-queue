package dev.slne.surf.queue.api.service

import dev.slne.surf.queue.api.InternalSurfQueueApi
import dev.slne.surf.queue.api.SurfQueue
import dev.slne.surf.api.core.util.requiredService
import dev.slne.surf.api.core.util.requiredService

/**
 * Internal service interface responsible for creating and caching [SurfQueue] instances.
 *
 * The concrete implementation is
 * loaded via `@AutoService` and accessed through the [instance] companion property.
 * This interface is marked as [InternalSurfQueueApi] and should not be used directly;
 * prefer [SurfQueue.byServer] instead.
 */
@InternalSurfQueueApi
interface SurfQueueService {
    /**
     * Returns the [SurfQueue] for the given [serverName], creating a new one if it
     * does not already exist in the cache.
     *
     * @param serverName the name of the target server
     * @return the queue instance for that server
     */
    fun getQueueByName(serverName: String): SurfQueue

    companion object {
        val instance = requiredService<SurfQueueService>()
    }
}