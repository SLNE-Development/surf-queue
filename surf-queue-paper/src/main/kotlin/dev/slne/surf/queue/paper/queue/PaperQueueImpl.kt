package dev.slne.surf.queue.paper.queue

import dev.slne.surf.queue.common.queue.AbstractQueue
import dev.slne.surf.queue.paper.metrics.QueueMetrics
import dev.slne.surf.queue.paper.queue.cleanup.PaperQueueCleanup

class PaperQueueImpl(serverName: String) : AbstractQueue(serverName), PaperQueueCommon {
    private val cleanup = PaperQueueCleanup(this, store, lockManager)

    override fun onEnqueued() {
        QueueMetrics.recordEnqueue(serverName)
    }

    override fun onDequeued() {
        QueueMetrics.recordDequeue(serverName)
    }

    override suspend fun delete() {
        store.deleteAll()
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
}