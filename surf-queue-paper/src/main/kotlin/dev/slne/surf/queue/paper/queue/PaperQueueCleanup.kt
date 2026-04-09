package dev.slne.surf.queue.paper.queue

import dev.slne.surf.queue.common.queue.RedisQueueLockManager
import dev.slne.surf.queue.common.queue.RedisQueueStore
import dev.slne.surf.queue.paper.metrics.QueueMetrics
import dev.slne.surf.surfapi.core.api.util.logger
import java.time.Instant
import kotlin.collections.iterator

class PaperQueueCleanup(
    private val queue: PaperSurfQueue,
    private val store: RedisQueueStore,
    private val lockManager: RedisQueueLockManager
) {

    companion object {
        private val log = logger()
        private const val CLEANUP_INTERVAL_TICKS = 10L
    }

    suspend fun tick() {
        if (queue.getTickCount() % CLEANUP_INTERVAL_TICKS == 0L) {
            lockManager.withCleanupLock {
                cleanupExpiredEntries()
            }
        }
    }

    suspend fun cleanupExpiredEntries() {
        val now = Instant.now().toEpochMilli()
        val allLastSeen = store.readAllLastSeen()
        var removals = 0

        try {
            for ((uuid, lastSeenTime) in allLastSeen) {
                if (now - lastSeenTime >= PaperSurfQueue.GRACE_PERIOD_MS) {
                    try {
                        queue.dequeue(uuid)
                        removals++
                        log.atInfo()
                            .log("Cleanup: removed expired entry %s from queue %s", uuid, queue.serverName)
                    } catch (e: Exception) {
                        log.atWarning()
                            .withCause(e)
                            .log("Cleanup: dequeue failed for %s in queue %s, attempting forced removal", uuid, queue.serverName)

                        try {
                            store.removeAllFor(uuid)
                            removals++
                        } catch (e2: Exception) {
                            log.atWarning()
                                .withCause(e2)
                                .log("Cleanup: forced removal also failed for %s in queue %s", uuid, queue.serverName)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            log.atWarning()
                .withCause(e)
                .log("Failed to cleanup expired entries for queue %s", queue.serverName)
        }

        QueueMetrics.recordCleanupCycle(removals)
    }
}