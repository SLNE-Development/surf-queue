package dev.slne.surf.queue.common.queue.tick

import dev.slne.surf.api.core.util.logger
import dev.slne.surf.queue.common.queue.RedisQueueService
import kotlinx.coroutines.*
import java.util.concurrent.Executors
import kotlin.time.Duration.Companion.seconds

/**
 * Singleton that runs the periodic queue tick loop on a dedicated single thread.
 *
 * Every second it ticks all known queues via [dev.slne.surf.queue.common.queue.AbstractQueue.tick] and periodically
 * refreshes the queue list from Redis (every [dev.slne.surf.queue.common.queue.RedisQueueService.Companion.QUEUE_REFRESH_INTERVAL_SECONDS]).
 * Tick failures for individual queues are caught by [SafeQueueTick] so one queue
 * cannot crash the entire loop.
 */
@OptIn(ExperimentalCoroutinesApi::class, DelicateCoroutinesApi::class)
object QueueTicker {
    private val log = logger()

    private val dispatcher = Executors
        .newSingleThreadExecutor { r -> Thread(r, "QueueTicker") }
        .asCoroutineDispatcher()

    private val scope = CoroutineScope(
        SupervisorJob() +
                dispatcher +
                CoroutineExceptionHandler { _, t ->
                    log.atSevere()
                        .withCause(t)
                        .log("Unhandled exception in QueueTicker:")
                }
    )

    fun start() {
        var secondsElapsed = 0

        scope.launch {
            while (isActive) {
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
    }

    fun dispose() {
        scope.cancel("QueueTicker disposed")
    }
}