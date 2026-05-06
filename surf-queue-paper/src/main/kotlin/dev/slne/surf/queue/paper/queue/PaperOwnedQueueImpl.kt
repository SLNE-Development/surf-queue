package dev.slne.surf.queue.paper.queue

import dev.slne.surf.queue.common.queue.AbstractTickableQueue
import dev.slne.surf.queue.common.queue.tick.QueueScheduler
import dev.slne.surf.queue.common.queue.tick.SafeQueueTick
import dev.slne.surf.queue.paper.metrics.QueueMetrics
import dev.slne.surf.queue.paper.queue.cleanup.PaperQueueCleanup
import dev.slne.surf.queue.paper.queue.transfer.PaperQueueTransferProcessor
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class PaperOwnedQueueImpl(serverName: String, scheduler: QueueScheduler) :
    AbstractTickableQueue(serverName, scheduler), PaperQueueCommon {
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
        SafeQueueTick.tickSafeWithTimeout(this, "cleanup", 10.seconds) { cleanup.tick(tickCount) }
        SafeQueueTick.tickSafeWithTimeout(this, "transfers", 45.seconds) { transferProcessor.tick() }
    }

    override suspend fun forceCleanup() {
        cleanup.cleanupExpiredEntries()
    }

    override suspend fun fix(): QueueFixResult {
        val sizeBefore = store.size()
        val wasPaused = store.isPaused()
        val lockReset = lockManager.resetLocks()
        store.setPaused(false)
        cleanup.cleanupExpiredEntries()
        val sizeAfter = store.size()

        return QueueFixResult(sizeBefore, sizeAfter, wasPaused, lockReset)
    }

    override suspend fun delete() {
        store.deleteAll()
    }
}