package dev.slne.surf.queue.velocity.transfer

import com.velocitypowered.api.event.connection.PreTransferEvent
import dev.slne.surf.core.api.common.surfCoreApi
import dev.slne.surf.queue.common.queue.RedisQueue
import dev.slne.surf.queue.common.queue.RedisQueueService
import dev.slne.surf.queue.velocity.plugin
import dev.slne.surf.surfapi.core.api.util.logger
import kotlinx.coroutines.*
import kotlin.jvm.optionals.getOrNull
import kotlin.time.Duration.Companion.seconds

object TransferTask {

    private val log = logger()
    private val scope =
        CoroutineScope(Dispatchers.Default + CoroutineName("surf-queue-transfer") + SupervisorJob() + CoroutineExceptionHandler { context, throwable ->
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
    }

    fun shutdown() {
        scope.cancel("Shutting down transfer task.")
    }

    private suspend fun tick() {
        coroutineScope {
            for (queue in RedisQueueService.getAll()) {
                launch {
                    transfer(queue)
                }
            }
        }
    }

    private suspend fun transfer(queue: RedisQueue) {
        val coreServer = surfCoreApi.getServerByName(queue.serverName) ?: return
        if (coreServer.getPlayerCount() >= coreServer.maxPlayers) return
        val velocityServer = plugin.proxy.getServer(queue.serverName).getOrNull() ?: return

        var lastTransferSuccess = true
        while (lastTransferSuccess) {
            lastTransferSuccess = queue.tryTransfer { entry ->
                val corePlayer = surfCoreApi.getPlayer(entry.uuid)

                if (corePlayer == null) {

                }
            }
        }
    }
}