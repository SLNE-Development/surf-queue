package dev.slne.surf.queue.velocity.queue

import dev.slne.surf.core.api.common.player.SurfPlayer
import dev.slne.surf.core.api.common.server.SurfServer
import dev.slne.surf.core.api.common.server.connection.SurfServerConnectResult
import dev.slne.surf.core.api.common.surfCoreApi
import dev.slne.surf.surfapi.core.api.util.logger

class QueueTransfer(private val queue: RedisQueue) {

    companion object {
        private val log = logger()
    }

    suspend fun tick() {
        transfer()
    }

    private suspend fun transfer() {
        val coreServer = surfCoreApi.getServerByName(queue.serverName)
        if (coreServer == null) {
            // Server probably shutdown, delete the queue.
            queue.delete()
            RedisQueueService.delete(queue.serverName)
            return
        }

        if (coreServer.getPlayerCount() >= coreServer.maxPlayers) return
        val availableSlots = coreServer.maxPlayers - coreServer.getPlayerCount()

        queue.processTransfers(availableSlots) { entry ->
            try {
                val corePlayer = surfCoreApi.getPlayer(entry.uuid)
                if (corePlayer == null) {
                    RedisQueue.TransferAction.PLAYER_NOT_FOUND
                } else {
                    val currentPlayerServer = corePlayer.currentServer
                    val currentPlayerServerName = currentPlayerServer?.name

                    if (currentPlayerServer == null) { // Probably transferring to another proxy
                        RedisQueue.TransferAction.PLAYER_NOT_CONNECTED_TO_A_SERVER
                    } else if (currentPlayerServerName == queue.serverName) {
                        RedisQueue.TransferAction.PLAYER_ALREADY_ON_SERVER
                    } else {
                        tryTransferPlayer(corePlayer, coreServer)
                    }
                }

            } catch (e: Exception) {
                log.atWarning()
                    .withCause(e)
                    .log("Error during transfer for queue %s", queue.serverName)
                RedisQueue.TransferAction.ERROR
            }
        }
    }

    private suspend fun tryTransferPlayer(
        player: SurfPlayer,
        targetServer: SurfServer
    ): RedisQueue.TransferAction {
        val (status, message) = surfCoreApi.sendPlayerAwaiting(player, targetServer)

        return when (status) {
            SurfServerConnectResult.Status.SERVER_NOT_FOUND -> RedisQueue.TransferAction.SERVER_NOT_FOUND
            SurfServerConnectResult.Status.ALREADY_CONNECTED -> RedisQueue.TransferAction.PLAYER_ALREADY_ON_SERVER
            SurfServerConnectResult.Status.CONNECTION_CANCELLED -> RedisQueue.TransferAction.PLUGIN_CANCELLED_TRANSFER
            SurfServerConnectResult.Status.CONNECTION_IN_PROGRESS -> RedisQueue.TransferAction.PLAYER_ALREADY_CONNECTING
            SurfServerConnectResult.Status.SERVER_DISCONNECTED -> {
                if (targetServer.maxPlayers <= targetServer.getPlayerCount()) {
                    RedisQueue.TransferAction.SERVER_FULL
                } else {
                    RedisQueue.TransferAction.PLAYER_KICKED_FROM_SERVER
                }
            }

            SurfServerConnectResult.Status.SUCCESS -> RedisQueue.TransferAction.DONE
            SurfServerConnectResult.Status.UNKNOWN_ERROR -> RedisQueue.TransferAction.ERROR
        }
    }
}