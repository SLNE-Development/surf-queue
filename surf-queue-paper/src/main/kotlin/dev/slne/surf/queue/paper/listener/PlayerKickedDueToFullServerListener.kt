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
        .build<UUID, KickReason>()

    @EventHandler(priority = EventPriority.MONITOR)
    fun onPlayerServerFullCheck(event: PlayerServerFullCheckEvent) {
        if (!event.isAllowed) {
            event.playerProfile.id?.let { kicks.put(it, KickReason.FULL_SERVER) }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onAsyncPlayerPreLogin(event: AsyncPlayerPreLoginEvent) {
        val reason = when (event.loginResult) {
            AsyncPlayerPreLoginEvent.Result.KICK_FULL -> KickReason.FULL_SERVER
            AsyncPlayerPreLoginEvent.Result.KICK_WHITELIST -> KickReason.NOT_WHITELISTED
            else -> return
        }

        kicks.put(event.uniqueId, reason)
    }

    fun consumeKickReason(uuid: UUID): KickReason {
        return kicks.asMap().remove(uuid) ?: KickReason.OTHER
    }

    enum class KickReason {
        FULL_SERVER,
        NOT_WHITELISTED,
        OTHER
    }
}