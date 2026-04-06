package dev.slne.surf.queue.velocity.listener

import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.connection.DisconnectEvent
import com.velocitypowered.api.event.connection.PostLoginEvent
import dev.slne.surf.queue.common.queue.RedisQueueService
import dev.slne.surf.queue.velocity.queue.VelocitySurfQueue
import dev.slne.surf.surfapi.core.api.util.logger
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

object QueuePlayerListener {
    private val log = logger()

    /**
     * When a player logs in, clear their grace-period marker from all queues.
     * Uses coroutineScope so the event handler suspends until all clears complete,
     * preventing a race where the cleanup task evicts the player before the
     * grace period is cleared.
     */
    @Subscribe
    suspend fun onPostLogin(event: PostLoginEvent) {
        val uuid = event.player.uniqueId
        coroutineScope {
            for (queue in RedisQueueService.get().getAll()) {
                require(queue is VelocitySurfQueue) { "Queue must be VelocitySurfQueue" }
                launch {
                    try {
                        queue.markPlayerReconnected(uuid)
                    } catch (e: Exception) {
                        log.atWarning()
                            .withCause(e)
                            .log("Failed to clear grace period for %s in queue %s", uuid, queue.serverName)
                    }
                }
            }
        }
    }

    /**
     * When a player disconnects, mark their last-seen time in all queues they
     * belong to. Uses coroutineScope so the event handler suspends until all
     * marks complete.
     *
     * Note: We unconditionally call markPlayerDisconnected instead of checking
     * isQueued first; this avoids a TOCTOU race where the player is dequeued
     * between isQueued() and markPlayerDisconnected(). The mark is harmless for
     * players not in the queue — the cleanup task only acts on entries that
     * also exist in the scored set.
     */
    @Subscribe
    suspend fun onDisconnect(event: DisconnectEvent) {
        val uuid = event.player.uniqueId
        coroutineScope {
            for (queue in RedisQueueService.get().getAll()) {
                require(queue is VelocitySurfQueue)
                launch {
                    try {
                        queue.markPlayerDisconnected(uuid)
                    } catch (e: Exception) {
                        log.atWarning()
                            .withCause(e)
                            .log("Failed to mark disconnect for %s in queue %s", uuid, queue.serverName)
                    }
                }
            }
        }
    }
}