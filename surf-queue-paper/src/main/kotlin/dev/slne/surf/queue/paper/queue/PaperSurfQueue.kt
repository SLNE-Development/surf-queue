package dev.slne.surf.queue.paper.queue

import dev.slne.surf.queue.common.queue.AbstractSurfQueue
import dev.slne.surf.queue.common.queue.QueueEntry
import dev.slne.surf.surfapi.core.api.util.logger
import java.util.*
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.minutes

class PaperSurfQueue(serverName: String) : AbstractSurfQueue(serverName) {
    private val transferProcessor = PaperQueueTransferProcessor(serverName, store, lockManager, GRACE_PERIOD_MS)
    private val cleanup = PaperQueueCleanup(this, store, lockManager)

    private val tickCount = AtomicLong(0)

    companion object {
        private val log = logger()

        val GRACE_PERIOD_MS = 1.minutes.inWholeMilliseconds
    }

    fun getTickCount() = tickCount.get()

    suspend fun tickSecond() {
        tickCount.incrementAndGet()

        safeTick("cleanup") { cleanup.tick() }
        safeTick("transfers") { transferProcessor.tick() }
    }

    private inline fun safeTick(component: String, block: () -> Unit) {
        try {
            block()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            log.atWarning()
                .withCause(e)
                .log("Failed to tick %s for queue %s", component, serverName)
        }
    }

    suspend fun delete() {
        store.deleteAll()
    }

    suspend fun forceCleanup() {
        cleanup.cleanupExpiredEntries()
    }

    suspend fun getEntryMeta(uuid: UUID): QueueEntry? = store.getMeta(uuid)
    suspend fun getEntryScore(uuid: UUID): Double? = store.getScore(uuid)
    suspend fun getEntryLastSeen(uuid: UUID): Long? = store.getLastSeen(uuid)
    suspend fun getEntryRetryCount(uuid: UUID): Int? = store.getRetryCount(uuid)
}