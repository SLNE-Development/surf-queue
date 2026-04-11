package dev.slne.surf.queue.common.queue

import dev.slne.surf.queue.common.redis.RedisInstance

/**
 * Holds all Redis key names used by a single queue instance.
 *
 * Keys are namespaced under `surf-queue:queue:v2:<serverName>:` to avoid
 * collisions between queues and other Redis data.
 *
 * @property serverName the target server this key set belongs to
 */
data class RedisQueueKeys(
    val serverName: String,
) {
    /** Sorted set key holding UUID → score entries (the queue itself). */
    val entriesKey = "$QUEUE_PREFIX$serverName:entries"
    /** Hash key holding UUID → [QueueEntry] metadata. */
    val metaKey = "$QUEUE_PREFIX$serverName:meta"
    /** Hash key holding UUID → retry count for failed transfers. */
    val retryCountKey = "$QUEUE_PREFIX$serverName:retry-count"
    /** Hash key holding UUID → last-seen timestamp for grace-period tracking. */
    val lastSeenKey = "$QUEUE_PREFIX$serverName:lastseen"
    /** Lock key for distributed transfer synchronization. */
    val transferLockKey = "$QUEUE_PREFIX$serverName:transfer-lock"
    /** Lock key for distributed cleanup synchronization. */
    val cleanupLockKey = "$QUEUE_PREFIX$serverName:cleanup-lock"
    /** Bucket key storing the paused state (`1` = paused). */
    val pausedKey = "$QUEUE_PREFIX$serverName:paused"
    /** Bucket key storing the queue's epoch timestamp in milliseconds. */
    val epochMsKey = "$QUEUE_PREFIX$serverName$EPOCH_MS_SUFFIX"

    companion object {
        /** Common prefix for all queue-related Redis keys. */
        val QUEUE_PREFIX = RedisInstance.namespaced("queue:v2:")
        /** Glob pattern matching all epoch-ms keys, used to discover existing queues. */
        val EPOCH_MS_KEY_PATTERN = "$QUEUE_PREFIX*$EPOCH_MS_SUFFIX"
        /** Suffix appended to the epoch-ms bucket key. */
        const val EPOCH_MS_SUFFIX = ":epoch-ms"
    }
}
