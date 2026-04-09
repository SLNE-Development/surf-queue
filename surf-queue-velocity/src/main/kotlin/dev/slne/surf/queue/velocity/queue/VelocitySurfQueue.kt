package dev.slne.surf.queue.velocity.queue

import dev.slne.surf.queue.common.queue.AbstractSurfQueue
import dev.slne.surf.queue.velocity.metrics.QueueMetrics
import dev.slne.surf.surfapi.core.api.util.logger
import java.time.Instant
import java.util.*
import java.util.concurrent.atomic.AtomicInteger

class VelocitySurfQueue(serverName: String) : AbstractSurfQueue(serverName) {
    val display = QueueDisplay(this)
    private val ticks = AtomicInteger(0)

    companion object {
        private val log = logger()
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

    fun getTickCount(): Int = ticks.get()

    suspend fun tickSecond() {
        try {
            ticks.incrementAndGet()
            display.tick()
        } catch (e: Exception) {
            log.atWarning()
                .withCause(e)
                .log("Error during tickSecond for queue %s", serverName)
        }
    }
}