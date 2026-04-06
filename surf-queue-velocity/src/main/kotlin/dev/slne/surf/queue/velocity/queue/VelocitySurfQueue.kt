package dev.slne.surf.queue.velocity.queue

import dev.slne.surf.queue.common.queue.AbstractSurfQueue
import dev.slne.surf.queue.velocity.metrics.QueueMetrics
import dev.slne.surf.queue.velocity.queue.display.QueueDisplay
import dev.slne.surf.surfapi.core.api.util.logger
import java.time.Instant
import java.util.*
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.minutes

class VelocitySurfQueue(serverName: String) : AbstractSurfQueue(serverName) {
    private val transferProcessor = RedisQueueTransferProcessor(serverName, store, lockManager, GRACE_PERIOD_MS)
    private val cleanup = RedisQueueCleanup(this, store, lockManager)

    private val tickCount = AtomicLong(0)

    val display = QueueDisplay(this)

    companion object {
        private val log = logger()

        val GRACE_PERIOD_MS = 1.minutes.inWholeMilliseconds
        const val LOCK_LEASE_SECONDS = 30L
    }

    override fun onEnqueued() {
        QueueMetrics.recordEnqueue(serverName)
    }

    override fun onDequeued() {
        QueueMetrics.recordDequeue(serverName)
    }

    suspend fun markPlayerReconnected(uuid: UUID) {
        store.clearLastSeen(uuid)
    }

    suspend fun markPlayerDisconnected(uuid: UUID) {
        store.putLastSeen(uuid, Instant.now().toEpochMilli())
    }

    fun getTickCount() = tickCount.get()

    suspend fun tickSecond() {
        tickCount.incrementAndGet()
        QueueMetrics.recordTick()

        safeTick("cleanup") { cleanup.tick() }
        safeTick("transfers") { transferProcessor.tick() }
        safeTick("display") { display.tick() }
    }

    private inline fun safeTick(component: String, block: () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e // Never swallow coroutine cancellation
        } catch (e: Exception) {
            log.atWarning()
                .withCause(e)
                .log("Failed to tick %s for queue %s", component, serverName)
        }
    }

    suspend fun delete() {
        store.deleteAll()
    }

}