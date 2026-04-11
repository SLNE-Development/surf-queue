package dev.slne.surf.queue.common.queue

import dev.slne.surf.queue.common.queue.tick.SafeQueueTick
import dev.slne.surf.surfapi.core.api.util.logger
import kotlinx.coroutines.*
import kotlin.time.Duration.Companion.seconds

/**
 * Singleton that runs the periodic queue tick loop on a dedicated single thread.
 *
 * Every second it ticks all known queues via [AbstractQueue.tick] and periodically
 * refreshes the queue list from Redis (every [RedisQueueService.QUEUE_REFRESH_INTERVAL_SECONDS]).
 * Tick failures for individual queues are caught by [SafeQueueTick] so one queue
 * cannot crash the entire loop.
 */
@OptIn(ExperimentalCoroutinesApi::class)
object QueueTicker {
    private val log = logger()
    private val queueTickerScope =
        CoroutineScope(newSingleThreadContext("QueueTicker") + CoroutineExceptionHandler { _, throwable ->
            log.atSevere()
                .withCause(throwable)
                .log("Unhandled exception in QueueTicker:")
        })

    /** Starts the tick loop. Should be called once during startup. */
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

    /** Cancels the tick loop and releases the ticker's coroutine scope. */
    fun dispose() {
        queueTickerScope.cancel("QueueTicker disposed")
    }
}