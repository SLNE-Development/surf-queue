package dev.slne.surf.queue.velocity.queue

import com.github.benmanes.caffeine.cache.Caffeine
import dev.slne.surf.queue.common.redis.redisApi
import dev.slne.surf.redis.libs.redisson.api.options.KeysScanOptions
import kotlinx.coroutines.reactive.asFlow

object RedisQueueService {
    private val queues = Caffeine.newBuilder()
        .build<String, RedisQueue> { serverName -> RedisQueue(serverName) }

    fun get(serverName: String) = queues.get(serverName)
    fun getAll() = queues.asMap().values

    fun delete(serverName: String) = queues.invalidate(serverName)

    suspend fun fetchFromRedis() {
        redisApi.redissonReactive.keys
            .getKeys(
                KeysScanOptions.defaults()
                    .pattern(RedisQueue.REDIS_QUEUE_PREFIX + "*")
            ).asFlow()
            .collect {
                get(it.replaceFirst(RedisQueue.REDIS_QUEUE_PREFIX, ""))
            }
    }
}