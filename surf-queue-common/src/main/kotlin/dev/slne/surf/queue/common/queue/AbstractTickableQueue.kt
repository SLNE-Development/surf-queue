package dev.slne.surf.queue.common.queue

import dev.slne.surf.api.core.util.runAtFixedRate
import dev.slne.surf.queue.common.queue.tick.QueueScheduler
import dev.slne.surf.queue.common.queue.tick.SafeQueueTick
import kotlinx.coroutines.*
import org.jetbrains.annotations.MustBeInvokedByOverriders
import java.lang.AutoCloseable
import kotlin.time.Duration
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.Duration.Companion.seconds

abstract class AbstractTickableQueue(
    serverName: String,
    scheduler: QueueScheduler
) : AbstractQueue(serverName), AutoCloseable {
    private val dispatcher = scheduler.dispatcherFor(serverName)
    private val tickScope = scheduler.scopeFor(serverName)

    private var tickJob: Job? = null

    /** Number of times [tick] has been called since creation. */
    var tickCount = 0L
        private set

    fun startTicking(period: Duration = 1.seconds) {
        check(tickJob == null) { "Ticking already started" }
        tickScope.runAtFixedRate(period) {
            SafeQueueTick.tickSafe(this@AbstractTickableQueue, "heartbeat") {
                tick()
            }
        }
    }

    /**
     * Increments [tickCount]. Subclasses that override this **must** invoke `super.tick()`.
     */
    @MustBeInvokedByOverriders
    protected open suspend fun tick() {
        tickCount++
    }

    protected suspend fun <T> onQueueThread(block: suspend CoroutineScope.() -> T): T =
        withContext(dispatcher) { block() }

    override fun close() {
        tickJob?.cancel()
        tickScope.cancel("Ticking stopped for $serverName")
    }
}