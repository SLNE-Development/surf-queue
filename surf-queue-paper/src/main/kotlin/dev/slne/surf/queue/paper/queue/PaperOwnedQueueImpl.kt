package dev.slne.surf.queue.paper.queue

import dev.slne.surf.queue.common.queue.AbstractTickableQueue
import dev.slne.surf.queue.common.queue.tick.QueueScheduler
import dev.slne.surf.queue.common.queue.tick.SafeQueueTick
import dev.slne.surf.queue.paper.metrics.QueueMetrics
import dev.slne.surf.queue.paper.queue.cleanup.PaperQueueCleanup
import dev.slne.surf.queue.paper.queue.transfer.PaperQueueTransferProcessor
import kotlin.time.Duration.Companion.minutes

class PaperOwnedQueueImpl(serverName: String, scheduler: QueueScheduler) :
    AbstractTickableQueue(serverName, scheduler) {
    private val transferProcessor = PaperQueueTransferProcessor(serverName, store, lockManager, GRACE_PERIOD_MS)
    private val cleanup = PaperQueueCleanup(this, store, lockManager)

    companion object {
        val GRACE_PERIOD_MS = 1.minutes.inWholeMilliseconds
    }

    override fun onEnqueued() {
        QueueMetrics.recordEnqueue(serverName)
    }

    override fun onDequeued() {
        QueueMetrics.recordDequeue(serverName)
    }

    override suspend fun tick() {
        super.tick()
        QueueMetrics.recordTick()
        SafeQueueTick.tickSafe(this, "cleanup") { cleanup.tick(tickCount) }
        SafeQueueTick.tickSafe(this, "transfers") { transferProcessor.tick() }
    }

    suspend fun forceCleanup() {
        cleanup.cleanupExpiredEntries()
    }

    suspend fun delete() {
        store.deleteAll()
    }
}