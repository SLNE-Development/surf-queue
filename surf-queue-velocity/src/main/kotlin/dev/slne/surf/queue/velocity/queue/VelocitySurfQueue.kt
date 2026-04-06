package dev.slne.surf.queue.velocity.queue

import dev.slne.surf.queue.common.queue.AbstractSurfQueue
import dev.slne.surf.queue.velocity.metrics.QueueMetrics
import java.time.Instant
import java.util.*

class VelocitySurfQueue(serverName: String) : AbstractSurfQueue(serverName) {

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

    suspend fun delete() {
        store.deleteAll()
    }

}