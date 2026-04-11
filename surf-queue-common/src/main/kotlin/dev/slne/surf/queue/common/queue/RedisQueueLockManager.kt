package dev.slne.surf.queue.common.queue

import dev.slne.surf.queue.common.redis.redisApi
import dev.slne.surf.surfapi.core.api.util.logger
import kotlinx.coroutines.future.await

/**
 * Manages distributed Redis locks for queue transfer and cleanup operations.
 *
 * Each queue has two independent locks:
 * - **Transfer lock** — ensures only one node processes transfers at a time.
 * - **Cleanup lock** — ensures only one node runs expired-entry cleanup at a time.
 *
 * Both locks are non-blocking (`tryLock`): if the lock cannot be acquired, the
 * operation is simply skipped until the next tick.
 *
 * @param keys the [RedisQueueKeys] providing the lock key names
 */
class RedisQueueLockManager(private val keys: RedisQueueKeys) {
    private val transferLock = redisApi.redisson.getLock(keys.transferLockKey)
    private val cleanupLock = redisApi.redisson.getLock(keys.cleanupLockKey)

    companion object {
        private val log = logger()
    }

    /**
     * Attempts to acquire the transfer lock and executes [block] with the result.
     *
     * @param block receives `true` if the lock was acquired, `false` otherwise
     * @return the value returned by [block]
     */
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

    /**
     * Attempts to acquire the cleanup lock. If acquired, executes [block] and
     * releases the lock afterwards. If not acquired, returns immediately.
     */
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