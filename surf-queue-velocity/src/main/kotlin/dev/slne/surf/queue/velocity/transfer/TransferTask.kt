package dev.slne.surf.queue.velocity.transfer

import com.velocitypowered.api.proxy.ConnectionRequestBuilder.Status
import com.velocitypowered.api.proxy.Player
import com.velocitypowered.api.proxy.server.RegisteredServer
import dev.slne.surf.core.api.common.surfCoreApi
import dev.slne.surf.queue.common.redis.redisApi
import dev.slne.surf.queue.velocity.plugin
import dev.slne.surf.queue.velocity.queue.QueueEntry
import dev.slne.surf.queue.velocity.queue.RedisQueue
import dev.slne.surf.queue.velocity.queue.RedisQueueService
import dev.slne.surf.queue.velocity.redis.packet.TransferPlayerRequest
import dev.slne.surf.queue.velocity.redis.packet.TransferPlayerResponse
import dev.slne.surf.queue.velocity.redis.packet.TransferPlayerResponse.Result.*
import dev.slne.surf.redis.request.RequestTimeoutException
import dev.slne.surf.surfapi.core.api.util.logger
import kotlinx.coroutines.*
import kotlinx.coroutines.future.await
import kotlin.jvm.optionals.getOrNull
import kotlin.time.Duration.Companion.seconds

object TransferTask {

    private val log = logger()
    private val scope = CoroutineScope(
        Dispatchers.Default +
                CoroutineName("surf-queue-transfer") +
                SupervisorJob() +
                CoroutineExceptionHandler { context, throwable ->
                    log.atSevere()
                        .withCause(throwable)
                        .log("An exception occurred in the transfer task.")
                })

    fun startTransferring() {
        scope.launch {
            while (isActive) {
                delay(1.seconds)
                tick()
            }
        }

        scope.launch {
            while (isActive) {
                delay(30.seconds)
                cleanupTick()
            }
        }
    }

    fun shutdown() {
        scope.cancel("Shutting down transfer task.")
    }

    private suspend fun tick() {
        coroutineScope {
            for (queue in RedisQueueService.getAll()) {
                launch {
                    try {
                        transfer(queue)
                    } catch (e: Exception) {
                        log.atWarning()
                            .withCause(e)
                            .log("Error during transfer for queue %s", queue.serverName)
                    }
                }
            }
        }
    }

    private suspend fun cleanupTick() {
        coroutineScope {
            for (queue in RedisQueueService.getAll()) {
                launch {
                    try {
                        queue.cleanupExpiredEntries()
                    } catch (e: Exception) {
                        log.atWarning()
                            .withCause(e)
                            .log("Error during cleanup for queue %s", queue.serverName)
                    }
                }
            }
        }
    }


    private suspend fun transfer(queue: RedisQueue) {
        val coreServer = surfCoreApi.getServerByName(queue.serverName)
        if (coreServer == null) {
            // Server probably shutdown, delete the queue.
            queue.delete()
            RedisQueueService.delete(queue.serverName)
            return
        }


        if (coreServer.getPlayerCount() >= coreServer.maxPlayers) return
        val velocityServer = plugin.proxy.getServer(queue.serverName).getOrNull() ?: return

        val availableSlots = coreServer.maxPlayers - coreServer.getPlayerCount()
        queue.processTransfers(availableSlots) { entry ->
            try {
                val corePlayer = surfCoreApi.getPlayer(entry.uuid)
                if (corePlayer == null) {
                    RedisQueue.TransferResult.PLAYER_NOT_FOUND
                } else {
                    val currentServer = corePlayer.currentServer?.name
                    if (currentServer == null) { // Probably transferring to another proxy
                        RedisQueue.TransferResult.PLAYER_TRANSFERRING
                    } else if (currentServer == queue.serverName) {
                        RedisQueue.TransferResult.SUCCESS
                    } else {
                        val velocityPlayer = plugin.proxy.getPlayer(entry.uuid).getOrNull()
                        if (velocityPlayer == null) {
                            tryTransferOnOtherProxy(entry, queue.serverName)
                        } else {
                            transferOnThisProxy(velocityPlayer, velocityServer)
                        }
                    }
                }

            } catch (e: Exception) {
                log.atWarning()
                    .withCause(e)
                    .log("Error during transfer for queue %s", queue.serverName)
                RedisQueue.TransferResult.ERROR
            }
        }
    }

    private suspend fun transferOnThisProxy(
        player: Player,
        target: RegisteredServer
    ): RedisQueue.TransferResult {
        val status = player.createConnectionRequest(target)
            .connect()
            .await()
            .status

        return convertStatus(status)
    }

    private fun convertStatus(status: Status): RedisQueue.TransferResult {
        return when (status) {
            Status.SUCCESS, Status.ALREADY_CONNECTED, Status.CONNECTION_IN_PROGRESS -> RedisQueue.TransferResult.SUCCESS
            Status.CONNECTION_CANCELLED -> RedisQueue.TransferResult.ERROR
            Status.SERVER_DISCONNECTED -> RedisQueue.TransferResult.SERVER_FULL
        }
    }

    private suspend fun tryTransferOnOtherProxy(
        entry: QueueEntry,
        targetServerName: String
    ): RedisQueue.TransferResult {
        val request = TransferPlayerRequest(entry.uuid, targetServerName)

        return try {
            val response = redisApi.sendRequest<TransferPlayerResponse>(request, 30.seconds.inWholeMilliseconds)
            when (val result = response.result) {
                is Success -> convertStatus(result.status)
                Error -> RedisQueue.TransferResult.ERROR
                ServerNotFound -> RedisQueue.TransferResult.ERROR
            }
        } catch (_: RequestTimeoutException) {
            RedisQueue.TransferResult.ERROR
        }
    }
}