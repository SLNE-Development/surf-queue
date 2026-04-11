package dev.slne.surf.queue.paper.queue.transfer

import dev.slne.surf.core.api.common.SurfCoreApi
import dev.slne.surf.core.api.common.player.SurfPlayer
import dev.slne.surf.core.api.common.server.SurfServer
import dev.slne.surf.core.api.common.server.connection.SurfServerConnectResult
import dev.slne.surf.queue.paper.config.SurfQueueConfig
import dev.slne.surf.queue.paper.listener.PlayerKickedDueToFullServerListener
import dev.slne.surf.surfapi.core.api.util.logger
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import java.util.*
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.min
import kotlin.time.Duration.Companion.seconds

class PaperQueueTransfer(
    private val processor: PaperQueueTransferProcessor,
    private val serverName: String
) {

    companion object {
        private val log = logger()
    }

    suspend fun tryTransfer(): Int {
        val availableSlots = Bukkit.getMaxPlayers() - Bukkit.getOnlinePlayers().size

        if (availableSlots <= 0) return 0
        val coreServer = SurfServer[serverName] ?: return 0
        val maxTransfers = min(availableSlots, SurfQueueConfig.getConfig().maxTransfersPerSecond)

        return processor.processTransfers(maxTransfers) { (uuid) ->
            transferEntry(uuid, coreServer)
        }
    }

    private suspend fun transferEntry(uuid: UUID, targetServer: SurfServer): Pair<TransferAction, Component?> {
        try {
            val corePlayer = SurfCoreApi.getPlayer(uuid) ?: return TransferAction.PLAYER_NOT_FOUND to null
            val currentPlayerServer = corePlayer.currentServer
            val currentPlayerServerName = currentPlayerServer?.name
                ?: return TransferAction.PLAYER_NOT_CONNECTED_TO_A_SERVER to null // Probably transferring to another proxy

            if (currentPlayerServerName == serverName) {
                return TransferAction.PLAYER_ALREADY_ON_SERVER to null
            }

            return tryTransferPlayer(corePlayer, targetServer)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            log.atWarning()
                .withCause(e)
                .log("Error during transfer for queue %s", serverName)
            return TransferAction.ERROR to null
        }
    }

    private suspend fun tryTransferPlayer(
        player: SurfPlayer,
        targetServer: SurfServer
    ): Pair<TransferAction, Component?> {
        val (status, message) = try {
            withTimeout(30.seconds) {
                SurfCoreApi.sendPlayerAwaiting(player, targetServer)
            }
        } catch (e: TimeoutCancellationException) {
            log.atWarning()
                .withCause(e)
                .log("Timed out waiting for player %s to connect to server %s", player.uuid, targetServer.name)
            return TransferAction.TIMEOUT to null
        }

        return when (status) {
            SurfServerConnectResult.Status.SERVER_NOT_FOUND -> TransferAction.SERVER_NOT_FOUND
            SurfServerConnectResult.Status.ALREADY_CONNECTED -> TransferAction.PLAYER_ALREADY_ON_SERVER
            SurfServerConnectResult.Status.CONNECTION_CANCELLED -> TransferAction.PLUGIN_CANCELLED_TRANSFER
            SurfServerConnectResult.Status.CONNECTION_IN_PROGRESS -> TransferAction.PLAYER_ALREADY_CONNECTING
            SurfServerConnectResult.Status.SERVER_DISCONNECTED -> {
                if (PlayerKickedDueToFullServerListener.consumeWasKickedDueToFullServer(player.uuid)) {
                    TransferAction.SERVER_FULL
                } else {
                    TransferAction.PLAYER_KICKED_FROM_SERVER
                }
            }

            SurfServerConnectResult.Status.SUCCESS -> TransferAction.DONE
            SurfServerConnectResult.Status.UNKNOWN_ERROR -> TransferAction.ERROR
        } to message
    }
}