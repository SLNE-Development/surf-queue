package dev.slne.surf.queue.paper.listener

import com.github.benmanes.caffeine.cache.Caffeine
import com.sksamuel.aedile.core.expireAfterWrite
import io.papermc.paper.event.player.PlayerServerFullCheckEvent
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.AsyncPlayerPreLoginEvent
import java.util.*
import kotlin.time.Duration.Companion.minutes

/**
 * Tracks players who were denied login due to a full server.
 *
 * Uses a Caffeine cache (2-minute TTL, max 10 000 entries) to remember UUIDs
 * of players that received `KICK_FULL` or whose [PlayerServerFullCheckEvent]
 * was denied. The [PaperQueueTransfer] consumes these records to distinguish
 * "server full" disconnects from other kick reasons.
 */
object PlayerKickedDueToFullServerListener : Listener {
    private val kicks = Caffeine.newBuilder()
        .expireAfterWrite(2.minutes)
        .maximumSize(10000)
        .build<UUID, Boolean>()

    @EventHandler(priority = EventPriority.MONITOR)
    fun onPlayerServerFullCheck(event: PlayerServerFullCheckEvent) {
        if (!event.isAllowed) {
            event.playerProfile.id?.let { kicks.put(it, true) }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onAsyncPlayerPreLogin(event: AsyncPlayerPreLoginEvent) {
        if (event.loginResult == AsyncPlayerPreLoginEvent.Result.KICK_FULL) {
            event.uniqueId.let { kicks.put(it, true) }
        }
    }

    /**
     * Consumes and returns whether the player with the given [uuid] was
     * recently kicked due to a full server.
     *
     * @param uuid the player's unique identifier
     * @return `true` if the player was kicked due to a full server (entry is removed), `false` otherwise
     */
    fun consumeWasKickedDueToFullServer(uuid: UUID): Boolean {
        return kicks.asMap().remove(uuid) ?: false
    }
}