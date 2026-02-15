package dev.slne.surf.queue.velocity.queue

import dev.slne.surf.queue.common.queue.AbstractSurfQueue
import dev.slne.surf.queue.velocity.metrics.QueueMetrics
import dev.slne.surf.queue.velocity.queue.display.QueueDisplay
import dev.slne.surf.surfapi.core.api.util.logger
import java.time.Instant
import java.util.*
import java.util.concurrent.atomic.AtomicLong
import kotlin.time.Duration.Companion.minutes

class VelocitySurfQueue(override val serverName: String) : AbstractSurfQueue(serverName) {
    private val transferProcessor = RedisQueueTransferProcessor(serverName, store, GRACE_PERIOD_MS)
    private val cleanup = RedisQueueCleanup(this, store)

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

        try {
            cleanup.tick()
        } catch (e: Exception) {
            log.atWarning()
                .withCause(e)
                .log("Failed to tick cleanup for queue %s", serverName)
        }

        try {
            transferProcessor.tick()
            println("Ticking transfers for queue $serverName:")
        } catch (e: Exception) {
            log.atWarning()
                .withCause(e)
                .log("Failed to tick transfers for queue %s", serverName)
        }

        try {
            display.tick()
            println("Ticking display for queue $serverName:")
        } catch (e: Exception) {
            log.atWarning()
                .withCause(e)
                .log("Failed to tick display for queue %s", serverName)
        }
    }

    suspend fun delete() {
        store.deleteAll()
    }

    enum class TransferAction {
        DONE,
        PLAYER_NOT_FOUND,
        PLAYER_NOT_CONNECTED_TO_A_SERVER,
        PLAYER_ALREADY_ON_SERVER,
        PLUGIN_CANCELLED_TRANSFER,
        PLAYER_KICKED_FROM_SERVER,
        SERVER_FULL,
        PLAYER_ALREADY_CONNECTING,
        SERVER_NOT_FOUND,
        ERROR,
    }
}