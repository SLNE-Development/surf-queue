package dev.slne.surf.queue.client.queue

import dev.slne.surf.queue.client.metrics.QueueMetrics
import dev.slne.surf.queue.client.queue.cleanup.QueueCleanup
import dev.slne.surf.queue.common.queue.AbstractQueue

class ClientQueueImpl(serverName: String) : AbstractQueue(serverName), ClientQueue {
    private val cleanup = QueueCleanup(this, store, lockManager)

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
