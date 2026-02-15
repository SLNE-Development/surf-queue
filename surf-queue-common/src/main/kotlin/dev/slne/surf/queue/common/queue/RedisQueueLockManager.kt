package dev.slne.surf.queue.common.queue

import dev.slne.surf.queue.common.redis.redisApi
import kotlinx.coroutines.future.await

class RedisQueueLockManager(private val keys: RedisQueueKeys) {
    private val transferLock = redisApi.redisson.getLock(keys.transferLockKey)
    private val cleanupLock = redisApi.redisson.getLock(keys.cleanupLockKey)

    suspend fun <T> withTransferLock(
        block: suspend (acquired: Boolean) -> T
    ): T {
        val threadId = Thread.currentThread().threadId()
        val acquired = transferLock.tryLockAsync(threadId).await()
        if (!acquired) return block(false)

        try {
            return block(true)
        } finally {
            transferLock.unlockAsync(threadId).await()
        }
    }

    suspend fun withCleanupLock(block: suspend () -> Unit) {
        val threadId = Thread.currentThread().threadId()
        val acquired = cleanupLock.tryLockAsync(threadId).await()
        if (!acquired) return

        try {
            block()
        } finally {
            cleanupLock.unlockAsync(threadId).await()
        }
    }
}