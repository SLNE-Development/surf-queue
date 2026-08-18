package dev.slne.surf.queue.minestom.platform

import com.google.auto.service.AutoService
import dev.slne.minestom.lobby.api.coroutine.minestomAsyncScope
import dev.slne.minestom.lobby.api.extension.ConnectionManager
import dev.slne.surf.core.api.common.server.SurfServer
import dev.slne.surf.queue.client.platform.QueuePlatform
import dev.slne.surf.queue.client.platform.TransferKickReason
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.util.*

@AutoService(QueuePlatform::class)
class MinestomQueuePlatform : QueuePlatform {
    override fun maxPlayers() = SurfServer.current().maxPlayers

    override fun onlinePlayerCount() = ConnectionManager.onlinePlayerCount

    /**
     * Minestom exposes no rejection reason, so every refused connection is reported as
     * [TransferKickReason.OTHER].
     */
    override fun consumeKickReason(uuid: UUID) = TransferKickReason.OTHER

    override fun launchAsync(block: suspend CoroutineScope.() -> Unit) =
        minestomAsyncScope.launch(block = block)
}
