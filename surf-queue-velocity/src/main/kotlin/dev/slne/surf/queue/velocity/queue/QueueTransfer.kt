package dev.slne.surf.queue.velocity.queue

import dev.slne.surf.core.api.common.player.SurfPlayer
import dev.slne.surf.core.api.common.server.SurfServer
import dev.slne.surf.core.api.common.server.connection.SurfServerConnectResult
import dev.slne.surf.core.api.common.surfCoreApi
import dev.slne.surf.surfapi.core.api.util.logger

class QueueTransfer(private val processor: RedisQueueTransferProcessor, private val serverName: String) {

    companion object {
        private val log = logger()
    }

    suspend fun tryTransfer(): Int {
        val coreServer = surfCoreApi.getServerByName(serverName) ?: return 0

        val playerCount = coreServer.getPlayerCount()
        val maxPlayers = coreServer.maxPlayers
        val availableSlots = maxPlayers - playerCount

        if (availableSlots <= 0) return 0

        return processor.processTransfers(availableSlots) { entry ->
            try {
                val corePlayer = surfCoreApi.getPlayer(entry.uuid)
                if (corePlayer == null) {
                    TransferAction.PLAYER_NOT_FOUND
                } else {
                    val currentPlayerServer = corePlayer.currentServer
                    val currentPlayerServerName = currentPlayerServer?.name

                    if (currentPlayerServer == null) { // Probably transferring to another proxy
                        TransferAction.PLAYER_NOT_CONNECTED_TO_A_SERVER
                    } else if (currentPlayerServerName == serverName) {
                        TransferAction.PLAYER_ALREADY_ON_SERVER
                    } else {
                        tryTransferPlayer(corePlayer, coreServer)
                    }
                }

            } catch (e: Exception) {
                log.atWarning()
                    .withCause(e)
                    .log("Error during transfer for queue %s", serverName)
                TransferAction.ERROR
            }
        }
    }

    private suspend fun tryTransferPlayer(
        player: SurfPlayer,
        targetServer: SurfServer
    ): TransferAction {
        val (status, message) = surfCoreApi.sendPlayerAwaiting(player, targetServer)

        return when (status) {
            SurfServerConnectResult.Status.SERVER_NOT_FOUND -> TransferAction.SERVER_NOT_FOUND
            SurfServerConnectResult.Status.ALREADY_CONNECTED -> TransferAction.PLAYER_ALREADY_ON_SERVER
            SurfServerConnectResult.Status.CONNECTION_CANCELLED -> TransferAction.PLUGIN_CANCELLED_TRANSFER
            SurfServerConnectResult.Status.CONNECTION_IN_PROGRESS -> TransferAction.PLAYER_ALREADY_CONNECTING
            SurfServerConnectResult.Status.SERVER_DISCONNECTED -> {
                if (targetServer.maxPlayers <= targetServer.getPlayerCount()) {
                    TransferAction.SERVER_FULL
                } else {
                    TransferAction.PLAYER_KICKED_FROM_SERVER
                }
            }

            SurfServerConnectResult.Status.SUCCESS -> TransferAction.DONE
            SurfServerConnectResult.Status.UNKNOWN_ERROR -> TransferAction.ERROR
        }
    }
}