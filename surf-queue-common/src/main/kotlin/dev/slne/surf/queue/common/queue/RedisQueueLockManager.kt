package dev.slne.surf.queue.common.queue

import dev.slne.surf.queue.common.redis.redisApi
import dev.slne.surf.surfapi.core.api.util.logger
import kotlinx.coroutines.future.await

class RedisQueueLockManager(private val keys: RedisQueueKeys) {
    private val transferLock = redisApi.redisson.getLock(keys.transferLockKey)
    private val cleanupLock = redisApi.redisson.getLock(keys.cleanupLockKey)

    companion object {
        private val log = logger()

        /**
         * Fixed sentinel thread ID used for all lock operations.
         * Coroutines can resume on any thread after a suspension point, so using
         * Thread.currentThread().threadId() would capture different IDs for lock
         * and unlock, causing Redisson to refuse the unlock (wrong owner).
         * A fixed ID avoids this mismatch entirely.
         */
        private const val LOCK_OWNER_ID = -1L
    }

    suspend fun <T> withTransferLock(
        block: suspend (acquired: Boolean) -> T
    ): T {
        val acquired = transferLock.tryLockAsync(LOCK_OWNER_ID).await()
        if (!acquired) return block(false)

        try {
            return block(true)
        } finally {
            try {
                transferLock.unlockAsync(LOCK_OWNER_ID).await()
            } catch (e: Exception) {
                log.atWarning()
                    .withCause(e)
                    .log("Failed to release transfer lock for %s", keys.serverName)
            }
        }
    }

    suspend fun withCleanupLock(block: suspend () -> Unit) {
        val acquired = cleanupLock.tryLockAsync(LOCK_OWNER_ID).await()
        if (!acquired) return

        try {
            block()
        } finally {
            try {
                cleanupLock.unlockAsync(LOCK_OWNER_ID).await()
            } catch (e: Exception) {
                log.atWarning()
                    .withCause(e)
                    .log("Failed to release cleanup lock for %s", keys.serverName)
            }
        }
    }
}