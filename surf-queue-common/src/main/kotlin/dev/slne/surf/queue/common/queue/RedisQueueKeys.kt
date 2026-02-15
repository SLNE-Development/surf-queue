package dev.slne.surf.queue.common.queue

import dev.slne.surf.queue.common.redis.RedisInstance

data class RedisQueueKeys(
    val serverName: String,
) {
    val entriesKey = "$QUEUE_PREFIX$serverName:entries"
    val metaKey = "$QUEUE_PREFIX$serverName:meta"
    val retryCountKey = "$QUEUE_PREFIX$serverName:retry-count"
    val lastSeenKey = "$QUEUE_PREFIX$serverName:lastseen"
    val transferLockKey = "$QUEUE_PREFIX$serverName:transfer-lock"
    val cleanupLockKey = "$QUEUE_PREFIX$serverName:cleanup-lock"
    val pausedKey = "$QUEUE_PREFIX$serverName:paused"
    val epochMsKey = "$QUEUE_PREFIX$serverName$EPOCH_MS_SUFFIX"

    companion object {
        val QUEUE_PREFIX = RedisInstance.namespaced("queue:")
        val EPOCH_MS_KEY_PATTERN = "$QUEUE_PREFIX*:$EPOCH_MS_SUFFIX"
        const val EPOCH_MS_SUFFIX = ":epoch-ms"
    }
}
