package dev.slne.surf.queue.velocity.queue

import dev.slne.surf.queue.common.queue.RedisQueueService
import dev.slne.surf.surfapi.core.api.util.logger
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
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