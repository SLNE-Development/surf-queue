package dev.slne.surf.queue.common.queue

import com.github.benmanes.caffeine.cache.Caffeine
import com.google.auto.service.AutoService
import dev.slne.surf.queue.api.InternalSurfQueueApi
import dev.slne.surf.queue.api.service.SurfQueueService
import dev.slne.surf.queue.common.SurfQueueInstance
import dev.slne.surf.queue.common.redis.redisApi
import dev.slne.surf.redis.libs.redisson.api.options.KeysScanOptions
import kotlinx.coroutines.reactive.asFlow

@OptIn(InternalSurfQueueApi::class)
@AutoService(SurfQueueService::class)
class RedisQueueService : SurfQueueService {
    private val queues = Caffeine.newBuilder()
        .build<String, AbstractSurfQueue> { serverName -> SurfQueueInstance.get().createQueue(serverName) }

    override fun get(serverName: String) = queues.get(serverName)
    fun getAll() = queues.asMap().values

    fun delete(serverName: String) = queues.invalidate(serverName)

    suspend fun fetchFromRedis() {
        redisApi.redissonReactive.keys
            .getKeys(
                KeysScanOptions.defaults()
                    .pattern(RedisQueueKeys.QUEUE_PREFIX + "*")
            ).asFlow()
            .collect {
                get(it.replaceFirst(RedisQueueKeys.QUEUE_PREFIX, ""))
            }
    }

    companion object {
        fun get() = SurfQueueService.instance as RedisQueueService
    }
}