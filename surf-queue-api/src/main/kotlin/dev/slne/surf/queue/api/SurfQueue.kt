package dev.slne.surf.queue.api

import dev.slne.surf.core.api.common.server.SurfServer
import dev.slne.surf.core.api.common.surfCoreApi
import dev.slne.surf.queue.api.service.SurfQueueService
import it.unimi.dsi.fastutil.objects.Object2IntMap
import java.util.*

interface SurfQueue {
    val serverName: String

    fun server() = surfCoreApi.getServerByName(serverName)

    suspend fun enqueue(uuid: UUID, priority: Int): Boolean
    suspend fun dequeue(uuid: UUID): Boolean
    suspend fun isQueued(uuid: UUID): Boolean
    suspend fun getPosition(uuid: UUID): Int?
    suspend fun size(): Int

    suspend fun getAllUuidsWithPosition(): Collection<Object2IntMap.Entry<UUID>>

    @OptIn(InternalSurfQueueApi::class)
    companion object {
        fun byServer(serverName: String) = SurfQueueService.instance.get(serverName)
        fun byServer(server: SurfServer) = byServer(server.name)
    }
}

fun SurfServer.queue() = SurfQueue.byServer(this)