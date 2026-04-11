package dev.slne.surf.queue.common.queue

import com.github.benmanes.caffeine.cache.Caffeine
import com.google.auto.service.AutoService
import dev.slne.surf.queue.api.InternalSurfQueueApi
import dev.slne.surf.queue.api.service.SurfQueueService
import dev.slne.surf.queue.common.QueueInstance
import dev.slne.surf.queue.common.redis.redisApi
import dev.slne.surf.redis.libs.redisson.api.options.KeysScanOptions
import kotlinx.coroutines.reactive.collect
import java.time.Duration

/**
 * Caffeine-cached queue factory implementing [SurfQueueService].
 *
 * Queues are created on-demand via [QueueInstance.createQueue] and cached with
 * a TTL of `4 × QUEUE_REFRESH_INTERVAL_SECONDS`. The [fetchFromRedis] method
 * discovers existing queues by scanning for epoch-ms keys in Redis.
 */
@OptIn(InternalSurfQueueApi::class)
@AutoService(SurfQueueService::class)
class RedisQueueService : SurfQueueService {
    private val queues = Caffeine.newBuilder()
        .expireAfterWrite(Duration.ofSeconds(QUEUE_REFRESH_INTERVAL_SECONDS * 4L))
        .build<String, AbstractQueue> { serverName -> QueueInstance.get().createQueue(serverName) }

    override fun get(serverName: String) = queues.get(serverName)
    /** Returns all currently cached queue instances. */
    fun getAll() = queues.asMap().values

    /** Invalidates (removes) the cached queue for [serverName]. */
    fun delete(serverName: String) = queues.invalidate(serverName)

    /**
     * Scans Redis for existing queue epoch-ms keys and warms the cache
     * by calling [get] for each discovered server name.
     */
    suspend fun fetchFromRedis() {
        redisApi.redissonReactive.keys
            .getKeys(
                KeysScanOptions.defaults()
                    .pattern(RedisQueueKeys.EPOCH_MS_KEY_PATTERN)
            ).map(::extractServerName)
            .collect(::get)
    }

    private fun extractServerName(key: String) = key
        .replaceFirst(RedisQueueKeys.QUEUE_PREFIX, "")
        .replaceFirst(RedisQueueKeys.EPOCH_MS_SUFFIX, "")

    companion object {
        /** Interval in seconds between automatic queue list refreshes. */
        const val QUEUE_REFRESH_INTERVAL_SECONDS = 30

        /** Convenience accessor that casts the [SurfQueueService.instance] to [RedisQueueService]. */
        fun get() = SurfQueueService.instance as RedisQueueService
    }
}