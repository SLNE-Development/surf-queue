package dev.slne.surf.queue.common.queue

import dev.slne.surf.queue.common.redis.redisApi
import dev.slne.surf.api.core.util.logger
import kotlinx.coroutines.future.await

class RedisQueueLockManager(private val keys: RedisQueueKeys) {
    private val transferLock = redisApi.redisson.getLock(keys.transferLockKey)
    private val cleanupLock = redisApi.redisson.getLock(keys.cleanupLockKey)

    companion object {
        private val log = logger()
    }

    suspend fun <T> withTransferLock(
        block: suspend (acquired: Boolean) -> T
    ): T {
        val threadId = Thread.currentThread().threadId()
        val acquired = transferLock.tryLockAsync(threadId).await()
        if (!acquired) return block(false)

        try {
            return block(true)
        } finally {
            try {
                transferLock.unlockAsync(threadId).await()
            } catch (e: Exception) {
                log.atWarning()
                    .withCause(e)
                    .log("Failed to release transfer lock for %s", keys.serverName)
            }
        }
    }

    suspend fun withCleanupLock(block: suspend () -> Unit) {
        val threadId = Thread.currentThread().threadId()
        val acquired = cleanupLock.tryLockAsync(threadId).await()
        if (!acquired) return

        try {
            block()
        } finally {
            try {
                cleanupLock.unlockAsync(threadId).await()
            } catch (e: Exception) {
                log.atWarning()
                    .withCause(e)
                    .log("Failed to release cleanup lock for %s", keys.serverName)
            }
        }
    }
}