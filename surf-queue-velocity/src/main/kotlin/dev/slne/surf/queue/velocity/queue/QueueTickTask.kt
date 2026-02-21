package dev.slne.surf.queue.velocity.queue

import dev.slne.surf.queue.common.queue.RedisQueueService
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

    private var lastFetch = 0L

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
        val now = System.currentTimeMillis()
        if (now - lastFetch > 30_000) {
            lastFetch = now
            RedisQueueService.get().fetchFromRedis()
        }

        coroutineScope {
            for (queue in RedisQueueService.get().getAll()) {
                require(queue is VelocitySurfQueue) { "Queue must be VelocitySurfQueue" }
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