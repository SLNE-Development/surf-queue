package dev.slne.surf.queue.client.queue.transfer

import dev.slne.surf.api.core.util.logger
import dev.slne.surf.core.api.common.SurfCoreApi
import dev.slne.surf.core.api.common.player.SurfPlayer
import dev.slne.surf.core.api.common.server.SurfServer
import dev.slne.surf.core.api.common.server.connection.SurfServerConnectResult
import dev.slne.surf.queue.client.platform.QueuePlatform
import dev.slne.surf.queue.client.platform.TransferKickReason
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import net.kyori.adventure.text.Component
import java.util.*
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

class QueueTransfer(private val serverName: String) {

    companion object {
        private val log = logger()
        private val CONNECT_TIMEOUT = 30.seconds
    }

    sealed interface Preparation {
        class Ready(val player: SurfPlayer) : Preparation
        class Rejected(val action: TransferAction) : Preparation
    }

    fun prepare(uuid: UUID): Preparation {
        try {
            val corePlayer = SurfCoreApi.getPlayer(uuid)
                ?: return Preparation.Rejected(TransferAction.PLAYER_NOT_FOUND)
            val currentPlayerServerName = corePlayer.currentServer?.name
                ?: return Preparation.Rejected(TransferAction.PLAYER_NOT_CONNECTED_TO_A_SERVER) // Probably transferring to another proxy

            if (currentPlayerServerName == serverName) {
                return Preparation.Rejected(TransferAction.PLAYER_ALREADY_ON_SERVER)
            }

            return Preparation.Ready(corePlayer)
        } catch (e: Exception) {
            log.atWarning()
                .withCause(e)
                .log("Error while preparing transfer of %s for queue %s", uuid, serverName)
            return Preparation.Rejected(TransferAction.ERROR)
        }
    }

    /**
     * Sends [player] to [targetServer] and awaits the outcome for at most [CONNECT_TIMEOUT].
     */
    suspend fun connect(player: SurfPlayer, targetServer: SurfServer): Pair<TransferAction, Component?> {
        val start = TimeSource.Monotonic.markNow()
        val (status, message) = try {
            withTimeout(CONNECT_TIMEOUT) {
                SurfCoreApi.sendPlayerAwaiting(player, targetServer)
            }
        } catch (_: TimeoutCancellationException) {
            log.atWarning()
                .log(
                    "Timed out waiting for player %s to connect to server %s after %d ms",
                    player.uuid,
                    targetServer.name,
                    start.elapsedNow().inWholeMilliseconds
                )
            return TransferAction.TIMEOUT to null
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            log.atWarning()
                .withCause(e)
                .log("Error during transfer of %s for queue %s", player.uuid, serverName)
            return TransferAction.ERROR to null
        }

        log.atInfo()
            .log(
                "Connection of %s to %s finished with %s after %d ms",
                player.uuid,
                targetServer.name,
                status,
                start.elapsedNow().inWholeMilliseconds
            )

        return when (status) {
            SurfServerConnectResult.Status.SERVER_NOT_FOUND -> TransferAction.SERVER_NOT_FOUND
            SurfServerConnectResult.Status.ALREADY_CONNECTED -> TransferAction.PLAYER_ALREADY_ON_SERVER
            SurfServerConnectResult.Status.CONNECTION_CANCELLED -> TransferAction.PLUGIN_CANCELLED_TRANSFER
            SurfServerConnectResult.Status.CONNECTION_IN_PROGRESS -> TransferAction.PLAYER_ALREADY_CONNECTING
            SurfServerConnectResult.Status.SERVER_DISCONNECTED -> {
                when (QueuePlatform.get().consumeKickReason(player.uuid)) {
                    TransferKickReason.FULL_SERVER -> TransferAction.SERVER_FULL
                    TransferKickReason.NOT_WHITELISTED -> TransferAction.NOT_WHITELISTED
                    TransferKickReason.OTHER -> TransferAction.PLAYER_KICKED_FROM_SERVER
                }
            }

            SurfServerConnectResult.Status.SUCCESS -> TransferAction.DONE
            SurfServerConnectResult.Status.UNKNOWN_ERROR -> TransferAction.ERROR
        } to message
    }
}
