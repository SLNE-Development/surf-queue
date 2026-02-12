package dev.slne.surf.queue.velocity.listener

import com.github.shynixn.mccoroutine.velocity.launch
import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.connection.PostLoginEvent
import dev.slne.surf.queue.velocity.plugin
import dev.slne.surf.queue.velocity.queue.RedisQueueService
import dev.slne.surf.surfapi.core.api.util.logger
import kotlinx.coroutines.launch

object QueuePlayerListener {
    private val log = logger()

    @Subscribe
    fun onPostLogin(event: PostLoginEvent) {
        val uuid = event.player.uniqueId
        plugin.container.launch {
            for (queue in RedisQueueService.getAll()) {
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
}