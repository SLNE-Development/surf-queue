package dev.slne.surf.queue.paper.queue

import dev.slne.surf.core.api.common.server.SurfServer
import dev.slne.surf.queue.api.SurfQueueAvailableSlotsProvider
import org.bukkit.Bukkit

object DefaultSurfQueueAvailableSlotsProvider : SurfQueueAvailableSlotsProvider {
    override suspend fun getAvailableSlots(server: SurfServer): Int {
        return if (SurfServer.current().name == server.name) {
            Bukkit.getMaxPlayers() - Bukkit.getOnlinePlayers().size
        } else {
            server.maxPlayers - server.getPlayers().size
        }
    }
}