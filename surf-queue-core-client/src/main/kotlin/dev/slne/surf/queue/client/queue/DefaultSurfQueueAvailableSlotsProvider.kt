package dev.slne.surf.queue.client.queue

import dev.slne.surf.core.api.common.server.SurfServer
import dev.slne.surf.queue.api.SurfQueueAvailableSlotsProvider
import dev.slne.surf.queue.client.platform.QueuePlatform

object DefaultSurfQueueAvailableSlotsProvider : SurfQueueAvailableSlotsProvider {
    override suspend fun getAvailableSlots(server: SurfServer): Int {
        return if (SurfServer.current().name == server.name) {
            QueuePlatform.get().maxPlayers() - QueuePlatform.get().onlinePlayerCount()
        } else {
            server.maxPlayers - server.getPlayers().size
        }
    }
}
