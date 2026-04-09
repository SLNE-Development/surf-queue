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

    fun consumeWasKickedDueToFullServer(uuid: UUID): Boolean {
        return kicks.asMap().remove(uuid) ?: false
    }
}