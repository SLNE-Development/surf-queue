package dev.slne.surf.queue.paper.listener

import com.github.benmanes.caffeine.cache.Caffeine
import com.sksamuel.aedile.core.expireAfterWrite
import dev.slne.surf.queue.client.platform.TransferKickReason
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
        .build<UUID, TransferKickReason>()

    @EventHandler(priority = EventPriority.MONITOR)
    fun onPlayerServerFullCheck(event: PlayerServerFullCheckEvent) {
        if (!event.isAllowed) {
            event.playerProfile.id?.let { kicks.put(it, TransferKickReason.FULL_SERVER) }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onAsyncPlayerPreLogin(event: AsyncPlayerPreLoginEvent) {
        val reason = when (event.loginResult) {
            AsyncPlayerPreLoginEvent.Result.KICK_FULL -> TransferKickReason.FULL_SERVER
            AsyncPlayerPreLoginEvent.Result.KICK_WHITELIST -> TransferKickReason.NOT_WHITELISTED
            else -> return
        }

        kicks.put(event.uniqueId, reason)
    }

    fun consumeKickReason(uuid: UUID): TransferKickReason {
        return kicks.asMap().remove(uuid) ?: TransferKickReason.OTHER
    }
}
