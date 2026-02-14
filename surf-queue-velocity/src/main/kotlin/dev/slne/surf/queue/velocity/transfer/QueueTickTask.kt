package dev.slne.surf.queue.velocity.transfer

import dev.slne.surf.queue.velocity.queue.RedisQueueService
import dev.slne.surf.surfapi.core.api.util.logger
import kotlinx.coroutines.*
import kotlin.time.Duration.Companion.seconds

object QueueTickTask {

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
    }

    fun shutdown() {
        scope.cancel("Shutting down transfer task.")
    }

    suspend fun tick() {
        coroutineScope {
            for (queue in RedisQueueService.getAll()) {
                launch {
                    try {
                        queue.tickSecond()
                    } catch (e: Exception) {
                        log.atWarning()
                            .withCause(e)
                            .log("Error during tickSecond for queue %s", queue.serverName)
                    }
                }
            }
        }
    }
}