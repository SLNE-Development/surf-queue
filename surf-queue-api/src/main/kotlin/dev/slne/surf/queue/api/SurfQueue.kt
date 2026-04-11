package dev.slne.surf.queue.api

import dev.slne.surf.core.api.common.SurfCoreApi
import dev.slne.surf.core.api.common.server.SurfServer
import dev.slne.surf.queue.api.service.SurfQueueService
import it.unimi.dsi.fastutil.objects.Object2IntMap
import it.unimi.dsi.fastutil.objects.ObjectList
import java.util.*

/**
 * Public API interface for a server-specific player queue.
 *
 * Each instance represents the queue for a single target server, identified by [serverName].
 * All queue operations are suspend functions and safe to call from any coroutine context.
 */
interface SurfQueue {

    /** The name of the target server this queue belongs to. */
    val serverName: String

    /**
     * Returns the [SurfServer] instance for this queue's target server.
     */
    fun server() = SurfCoreApi.getServerByName(serverName)

    /**
     * Enqueues [uuid] using the priority resolved from LuckPerms.
     *
     * @return `true` if the player was newly added, `false` if already queued.
     */
    suspend fun enqueue(uuid: UUID): Boolean

    /**
     * Enqueues [uuid] with an explicit [priority].
     * Priorities above the maximum representable value are capped automatically.
     *
     * @return `true` if the player was newly added, `false` if already queued.
     */
    suspend fun enqueue(uuid: UUID, priority: Int): Boolean

    /**
     * Removes [uuid] from the queue.
     *
     * @return `true` if the player was removed, `false` if they were not queued.
     */
    suspend fun dequeue(uuid: UUID): Boolean

    /**
     * Returns `true` if [uuid] is currently in the queue.
     */
    suspend fun isQueued(uuid: UUID): Boolean

    /**
     * Returns the 0-based position of [uuid] in the queue, or `null` if not queued.
     */
    suspend fun getPosition(uuid: UUID): Int?

    /**
     * Returns the total number of players currently in the queue.
     */
    suspend fun size(): Int

    /**
     * Returns `true` if the queue is paused. A paused queue stops processing transfers.
     */
    suspend fun isPaused(): Boolean

    /** Pauses the queue. */
    suspend fun pause()

    /** Resumes the queue. */
    suspend fun resume()

    /**
     * Returns all queued UUIDs together with their 1-based position.
     *
     * @deprecated Use [getAllUuidsOrderedByPosition] for better performance.
     */
    @Deprecated(
        "Use getAllUuidsOrderedByPosition for better performance",
        ReplaceWith("getAllUuidsOrderedByPosition()")
    )
    suspend fun getAllUuidsWithPosition(): ObjectList<Object2IntMap.Entry<UUID>>

    /**
     * Returns all queued UUIDs in ascending position order (position 1 first).
     */
    suspend fun getAllUuidsOrderedByPosition(): ObjectList<UUID>

    @OptIn(InternalSurfQueueApi::class)
    companion object {
        /**
         * Returns the [SurfQueue] for the given [serverName], or creates a new one if it doesn't exist.
         */
        fun byServer(serverName: String) = SurfQueueService.instance.get(serverName)

        /**
         * Returns the [SurfQueue] for the given [server], or creates a new one if it doesn't exist.
         */
        fun byServer(server: SurfServer) = byServer(server.name)
    }
}

/**
 * Convenience extension to retrieve the queue for this server.
 */
fun SurfServer.queue() = SurfQueue.byServer(this)