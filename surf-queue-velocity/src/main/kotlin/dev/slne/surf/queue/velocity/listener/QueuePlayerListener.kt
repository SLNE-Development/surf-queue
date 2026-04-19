package dev.slne.surf.queue.velocity.listener

import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.connection.DisconnectEvent
import com.velocitypowered.api.event.connection.PostLoginEvent
import dev.slne.surf.queue.common.queue.RedisQueueService
import dev.slne.surf.queue.velocity.queue.VelocityQueueImpl
import dev.slne.surf.api.core.util.logger
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

object QueuePlayerListener {
    private val log = logger()

    @Subscribe
    suspend fun onPostLogin(event: PostLoginEvent) {
        val uuid = event.player.uniqueId
        coroutineScope {
            for (queue in RedisQueueService.get().getAll()) {
                require(queue is VelocityQueueImpl) { "Queue must be VelocityQueueImpl" }
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

    @Subscribe
    suspend fun onDisconnect(event: DisconnectEvent) {
        val uuid = event.player.uniqueId
        coroutineScope {
            for (queue in RedisQueueService.get().getAll()) {
                require(queue is VelocityQueueImpl)
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