package dev.slne.surf.queue.common.queue

import dev.slne.surf.queue.common.queue.tick.SafeQueueTick
import dev.slne.surf.api.core.util.logger
import kotlinx.coroutines.*
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
object QueueTicker {
    private val log = logger()
    private val queueTickerScope =
        CoroutineScope(newSingleThreadContext("QueueTicker") + CoroutineExceptionHandler { _, throwable ->
            log.atSevere()
                .withCause(throwable)
                .log("Unhandled exception in QueueTicker:")
        })

    fun start() {
        var secondsElapsed = 0

        queueTickerScope.launch {
            delay(1.seconds)
            secondsElapsed++

            if (secondsElapsed % RedisQueueService.QUEUE_REFRESH_INTERVAL_SECONDS == 0) {
                RedisQueueService.get().fetchFromRedis()
            }

            for (queue in RedisQueueService.get().getAll()) {
                SafeQueueTick.tickSafe(queue, "heartbeat") {
                    queue.tick()
                }
            }
        }
    }

    fun dispose() {
        queueTickerScope.cancel("QueueTicker disposed")
    }
}