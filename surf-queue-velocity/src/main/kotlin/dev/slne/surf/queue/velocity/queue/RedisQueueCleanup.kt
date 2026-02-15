package dev.slne.surf.queue.velocity.queue

import dev.slne.surf.queue.common.queue.RedisQueueStore
import dev.slne.surf.surfapi.core.api.util.logger
import java.time.Instant

class RedisQueueCleanup(
    private val queue: VelocitySurfQueue,
    private val store: RedisQueueStore
) {

    companion object {
        private val log = logger()
    }

    suspend fun tick() {
        if (queue.getTickCount() % 30 == 0L) {
            store.tryWithCleanupLock {
                cleanupExpiredEntries()
            }
        }
    }

    suspend fun cleanupExpiredEntries() {
        val now = Instant.now().toEpochMilli()
        val allLastSeen = store.readAllLastSeen()

        try {
            for ((uuid, lastSeenTime) in allLastSeen) {
                if (now - lastSeenTime >= VelocitySurfQueue.GRACE_PERIOD_MS) {
                    try {
                        queue.dequeue(uuid)
                        log.atInfo()
                            .log("Cleanup: removed expired entry %s from queue %s", uuid, queue.serverName)
                    } catch (_: Exception) {
                        store.removeAllFor(uuid)
                    }
                }
            }
        } catch (e: Exception) {
            log.atWarning()
                .withCause(e)
                .log("Failed to cleanup expired entries for queue %s", queue.serverName)
        }
    }
}