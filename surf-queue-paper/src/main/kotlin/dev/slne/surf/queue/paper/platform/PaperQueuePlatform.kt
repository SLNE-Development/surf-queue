package dev.slne.surf.queue.paper.platform

import com.github.shynixn.mccoroutine.folia.launch
import com.google.auto.service.AutoService
import dev.slne.surf.queue.client.platform.QueuePlatform
import dev.slne.surf.queue.client.platform.TransferKickReason
import dev.slne.surf.queue.paper.listener.PlayerKickedDueToFullServerListener
import dev.slne.surf.queue.paper.plugin
import kotlinx.coroutines.CoroutineScope
import org.bukkit.Bukkit
import java.util.*

@AutoService(QueuePlatform::class)
class PaperQueuePlatform : QueuePlatform {
    override fun maxPlayers() = Bukkit.getMaxPlayers()

    override fun onlinePlayerCount() = Bukkit.getOnlinePlayers().size

    override fun consumeKickReason(uuid: UUID) =
        PlayerKickedDueToFullServerListener.consumeKickReason(uuid)

    override fun launchAsync(block: suspend CoroutineScope.() -> Unit) = plugin.launch(block = block)
}
