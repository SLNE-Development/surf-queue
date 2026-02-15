package dev.slne.surf.queue.common.queue

import dev.slne.surf.queue.common.redis.RedisInstance

data class RedisQueueKeys(
    val serverName: String,
    val prefix: String = QUEUE_PREFIX
) {
    val entriesKey = "$prefix$serverName:entries"
    val metaKey = "$prefix$serverName:meta"
    val lastSeenKey = "$prefix$serverName:lastseen"
    val transferLockKey = "$prefix$serverName:transfer-lock"
    val epochMsKey = "$prefix$serverName:epoch-ms"

    companion object {
        val QUEUE_PREFIX = RedisInstance.namespaced("queue:")
    }
}
