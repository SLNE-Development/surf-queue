package dev.slne.surf.queue.paper.queue

import dev.slne.surf.queue.common.queue.AbstractQueue
import dev.slne.surf.queue.paper.metrics.QueueMetrics
import dev.slne.surf.queue.paper.queue.cleanup.PaperQueueCleanup

class PaperQueueImpl(serverName: String) : AbstractQueue(serverName) {
    private val cleanup = PaperQueueCleanup(this, store, lockManager)

    override fun onEnqueued() {
        QueueMetrics.recordEnqueue(serverName)
    }

    override fun onDequeued() {
        QueueMetrics.recordDequeue(serverName)
    }

    suspend fun delete() {
        store.deleteAll()
    }

    suspend fun forceCleanup() {
        cleanup.cleanupExpiredEntries()
    }
}