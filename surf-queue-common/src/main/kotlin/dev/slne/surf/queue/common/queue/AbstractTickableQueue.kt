package dev.slne.surf.queue.common.queue

import dev.slne.surf.api.core.util.logger
import dev.slne.surf.api.core.util.runAtFixedRate
import dev.slne.surf.queue.common.queue.tick.QueueScheduler
import dev.slne.surf.queue.common.queue.tick.SafeQueueTick
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.MustBeInvokedByOverriders
import java.lang.AutoCloseable
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

abstract class AbstractTickableQueue(
    serverName: String,
    scheduler: QueueScheduler
) : AbstractQueue(serverName), AutoCloseable {
    companion object {
        private val log = logger()
    }

    private val dispatcher = scheduler.dispatcherFor(serverName)
    private val tickScope = scheduler.scopeFor(serverName)

    @Volatile
    private var tickJob: Job? = null

    /** Number of times [tick] has been called since creation. */
    var tickCount = 0L
        private set

    fun startTicking(period: Duration = 1.seconds) {
        check(tickJob == null) { "Ticking already started" }

        log.atInfo()
            .log("Starting ticking for queue $serverName with period $period")

        tickJob = tickScope.runAtFixedRate(period) {
            SafeQueueTick.tickSafe(this@AbstractTickableQueue, "heartbeat") {
                tick()
            }
        }

        tickJob?.invokeOnCompletion { cause ->
            if (cause == null) {
                log.atInfo()
                    .log("Tick job completed normally for queue $serverName")
            } else {
                log.atWarning()
                    .withCause(cause)
                    .log("Tick job failed for queue $serverName")
            }
        }
    }

    /**
     * Increments [tickCount]. Subclasses that override this **must** invoke `super.tick()`.
     */
    @MustBeInvokedByOverriders
    protected open suspend fun tick() {
        tickCount++

        epochMs()
    }

    protected suspend fun <T> onQueueThread(block: suspend CoroutineScope.() -> T): T =
        withContext(dispatcher) { block() }

    override fun close() {
        log.atInfo()
            .log("Closing queue $serverName")

        tickJob?.cancel()
        tickScope.cancel("Ticking stopped for $serverName")
    }
}