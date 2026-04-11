package dev.slne.surf.queue.paper.queue.cleanup

import dev.slne.surf.queue.common.queue.RedisQueueLockManager
import dev.slne.surf.queue.common.queue.RedisQueueStore
import dev.slne.surf.queue.paper.metrics.QueueMetrics
import dev.slne.surf.queue.paper.queue.PaperQueueImpl
import dev.slne.surf.surfapi.core.api.util.logger
import java.time.Instant
import java.util.UUID
import kotlin.collections.iterator
import kotlin.coroutines.cancellation.CancellationException

/**
 * Periodically removes expired queue entries (players who have been offline
 * longer than [PaperQueueImpl.GRACE_PERIOD_MS]).
 *
 * Runs every [CLEANUP_INTERVAL_TICKS] ticks under the distributed cleanup lock
 * to ensure only one server node performs cleanup at a time.
 *
 * @param queue the [PaperQueueImpl] this cleanup belongs to
 * @param store the [RedisQueueStore] for data access
 * @param lockManager the [RedisQueueLockManager] for distributed synchronization
 */
class PaperQueueCleanup(
    private val queue: PaperQueueImpl,
    private val store: RedisQueueStore,
    private val lockManager: RedisQueueLockManager
) {
    companion object {
        private val log = logger()
        private const val CLEANUP_INTERVAL_TICKS = 10L

        @JvmStatic
        private fun isExpired(now: Long, lastSeenTime: Long): Boolean {
            return now - lastSeenTime >= PaperQueueImpl.GRACE_PERIOD_MS
        }
    }

    /**
     * Called every tick. Runs [cleanupExpiredEntries] every [CLEANUP_INTERVAL_TICKS] ticks
     * under the cleanup lock.
     */
    suspend fun tick() {
        if (queue.tickCount % CLEANUP_INTERVAL_TICKS == 0L) {
            lockManager.withCleanupLock {
                cleanupExpiredEntries()
            }
        }
    }

    /**
     * Scans all last-seen entries and removes those that have expired beyond
     * the grace period.
     */
    suspend fun cleanupExpiredEntries() {
        val now = Instant.now().toEpochMilli()
        val allLastSeen = store.readAllLastSeen()
        var removals = 0

        try {
            for ((uuid, lastSeenTime) in allLastSeen) {
                if (isExpired(now, lastSeenTime)) {
                    removals += processExpiredEntry(uuid)
                }
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            log.atWarning()
                .withCause(e)
                .log("Failed to cleanup expired entries for queue %s", queue.serverName)
        }

        QueueMetrics.recordCleanupCycle(removals)
    }

    private suspend fun processExpiredEntry(uuid: UUID): Int = try {
        queue.dequeue(uuid)
        log.atInfo()
            .log("Cleanup: removed expired entry %s from queue %s", uuid, queue.serverName)
        1
    } catch (e: Exception) {
        if (e is CancellationException) throw e
        log.atWarning()
            .withCause(e)
            .log("Cleanup: dequeue failed for %s in queue %s, attempting forced removal", uuid, queue.serverName)
        forceRemoveEntry(uuid)
    }

    private suspend fun forceRemoveEntry(uuid: UUID): Int = try {
        store.removeAllFor(uuid)
        1
    } catch (e: Exception) {
        if (e is CancellationException) throw e
        log.atWarning()
            .withCause(e)
            .log("Cleanup: forced removal also failed for %s in queue %s", uuid, queue.serverName)
        0
    }
}