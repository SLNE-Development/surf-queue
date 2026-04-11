package dev.slne.surf.queue.api

import dev.slne.surf.core.api.common.SurfCoreApi
import dev.slne.surf.core.api.common.server.SurfServer
import dev.slne.surf.queue.api.service.SurfQueueService
import it.unimi.dsi.fastutil.objects.Object2IntMap
import it.unimi.dsi.fastutil.objects.ObjectList
import java.util.*

interface SurfQueue {
    val serverName: String

    fun server() = SurfCoreApi.getServerByName(serverName)

    suspend fun enqueue(uuid: UUID): Boolean
    suspend fun enqueue(uuid: UUID, priority: Int): Boolean
    suspend fun dequeue(uuid: UUID): Boolean
    suspend fun isQueued(uuid: UUID): Boolean
    suspend fun getPosition(uuid: UUID): Int?
    suspend fun size(): Int

    suspend fun isPaused(): Boolean
    suspend fun pause()
    suspend fun resume()

    @Deprecated(
        "Use getAllUuidsOrderedByPosition for better performance",
        ReplaceWith("getAllUuidsOrderedByPosition()")
    )
    suspend fun getAllUuidsWithPosition(): ObjectList<Object2IntMap.Entry<UUID>>

    suspend fun getAllUuidsOrderedByPosition(): ObjectList<UUID>

    @OptIn(InternalSurfQueueApi::class)
    companion object {
        fun byServer(serverName: String) = SurfQueueService.instance.get(serverName)
        fun byServer(server: SurfServer) = byServer(server.name)
    }
}

fun SurfServer.queue() = SurfQueue.byServer(this)