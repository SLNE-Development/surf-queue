package dev.slne.surf.queue.paper.queue

import dev.slne.surf.core.api.common.server.SurfServer
import dev.slne.surf.queue.common.queue.AbstractQueue
import dev.slne.surf.queue.common.queue.tick.SafeQueueTick
import dev.slne.surf.queue.paper.metrics.QueueMetrics
import dev.slne.surf.queue.paper.queue.cleanup.PaperQueueCleanup
import dev.slne.surf.queue.paper.queue.transfer.PaperQueueTransferProcessor
import kotlin.time.Duration.Companion.minutes

/**
 * Paper-specific queue implementation.
 *
 * Adds transfer processing, expired-entry cleanup, and metrics recording on
 * top of [AbstractQueue]. Only processes ticks if the current server **is**
 * the target server for this queue.
 *
 * @param serverName the name of the target server
 */
class PaperQueueImpl(serverName: String) : AbstractQueue(serverName) {
    private val transferProcessor = PaperQueueTransferProcessor(serverName, store, lockManager, GRACE_PERIOD_MS)
    private val cleanup = PaperQueueCleanup(this, store, lockManager)

    private val isTargetServer = SurfServer.current().name == serverName

    companion object {
        /** Grace period before an offline player is removed from the queue. */
        val GRACE_PERIOD_MS = 1.minutes.inWholeMilliseconds
    }

    override fun onEnqueued() {
        QueueMetrics.recordEnqueue(serverName)
    }

    override fun onDequeued() {
        QueueMetrics.recordDequeue(serverName)
    }

    /**
     * Called once per second. Runs cleanup and transfer processing if this
     * server is the target server for the queue.
     */
    suspend fun tickSecond() {
        if (isTargetServer) {
            QueueMetrics.recordTick()
            SafeQueueTick.tickSafe(this, "cleanup") { cleanup.tick() }
            SafeQueueTick.tickSafe(this, "transfers") { transferProcessor.tick() }
        }
    }

    /** Deletes all data for this queue from Redis. */
    suspend fun delete() {
        store.deleteAll()
    }

    /** Immediately runs expired-entry cleanup, bypassing the normal interval. */
    suspend fun forceCleanup() {
        cleanup.cleanupExpiredEntries()
    }
}