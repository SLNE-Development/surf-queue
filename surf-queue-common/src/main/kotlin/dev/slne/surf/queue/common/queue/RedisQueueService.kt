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

@OptIn(InternalSurfQueueApi::class)
@AutoService(SurfQueueService::class)
class RedisQueueService : SurfQueueService {
    private val queues = Caffeine.newBuilder()
        .expireAfterWrite(Duration.ofSeconds(QUEUE_REFRESH_INTERVAL_SECONDS * 4L))
        .build<String, AbstractQueue> { serverName -> QueueInstance.get().createQueue(serverName) }

    override fun get(serverName: String) = queues.get(serverName)
    fun getAll() = queues.asMap().values

    fun delete(serverName: String) = queues.invalidate(serverName)

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
        const val QUEUE_REFRESH_INTERVAL_SECONDS = 30
        fun get() = SurfQueueService.instance as RedisQueueService
    }
}