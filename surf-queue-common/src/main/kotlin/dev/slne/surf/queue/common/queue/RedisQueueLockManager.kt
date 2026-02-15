package dev.slne.surf.queue.common.queue

import dev.slne.surf.queue.common.redis.redisApi
import kotlinx.coroutines.future.await

class RedisQueueLockManager(private val keys: RedisQueueKeys) {
    private val transferLock = redisApi.redisson.getLock(keys.transferLockKey)
    private val cleanupLock = redisApi.redisson.getLock(keys.cleanupLockKey)

    suspend fun <T> withTransferLock(
        threadId: Long,
        block: suspend (acquired: Boolean) -> T
    ): T {
        val acquired = transferLock.tryLockAsync(threadId).await()
        if (!acquired) return block(false)

        try {
            return block(true)
        } finally {
            transferLock.unlockAsync(threadId).await()
        }
    }

    suspend fun withCleanupLock(block: suspend () -> Unit) {
        val acquired = cleanupLock.tryLockAsync().await()
        if (!acquired) return

        try {
            block()
        } finally {
            cleanupLock.unlockAsync().await()
        }
    }
}