package dev.slne.surf.queue.paper.queue

import dev.slne.surf.core.api.common.server.SurfServer
import dev.slne.surf.queue.common.queue.AbstractQueue
import dev.slne.surf.queue.common.queue.tick.SafeQueueTick
import dev.slne.surf.queue.paper.metrics.QueueMetrics
import dev.slne.surf.queue.paper.queue.cleanup.PaperQueueCleanup
import dev.slne.surf.queue.paper.queue.transfer.PaperQueueTransferProcessor
import kotlin.time.Duration.Companion.minutes

class PaperQueueImpl(serverName: String) : AbstractQueue(serverName) {
    private val transferProcessor = PaperQueueTransferProcessor(serverName, store, lockManager, GRACE_PERIOD_MS)
    private val cleanup = PaperQueueCleanup(this, store, lockManager)

    private val isTargetServer = SurfServer.current().name == serverName

    companion object {
        val GRACE_PERIOD_MS = 1.minutes.inWholeMilliseconds
    }

    override fun onEnqueued() {
        QueueMetrics.recordEnqueue(serverName)
    }

    override fun onDequeued() {
        QueueMetrics.recordDequeue(serverName)
    }

    suspend fun tickSecond() {
        if (isTargetServer) {
            QueueMetrics.recordTick()
            SafeQueueTick.tickSafe(this, "cleanup") { cleanup.tick() }
            SafeQueueTick.tickSafe(this, "transfers") { transferProcessor.tick() }
        }
    }

    suspend fun delete() {
        store.deleteAll()
    }

    suspend fun forceCleanup() {
        cleanup.cleanupExpiredEntries()
    }
}