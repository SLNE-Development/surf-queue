package dev.slne.surf.queue.common.queue

import com.github.benmanes.caffeine.cache.Caffeine
import com.google.auto.service.AutoService
import dev.slne.surf.queue.api.InternalSurfQueueApi
import dev.slne.surf.queue.api.service.SurfQueueService
import dev.slne.surf.queue.common.SurfQueueInstance
import dev.slne.surf.queue.common.redis.redisApi
import dev.slne.surf.redis.libs.redisson.api.options.KeysScanOptions
import dev.slne.surf.surfapi.core.api.util.logger
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
                    .pattern(RedisQueueKeys.EPOCH_MS_KEY_PATTERN)
            ).asFlow()
            .collect {
                val serverName =
                    it.replaceFirst(RedisQueueKeys.QUEUE_PREFIX, "").replaceFirst(RedisQueueKeys.EPOCH_MS_SUFFIX, "")

                log.atInfo()
                    .log("Found queue for server $serverName in Redis, fetching...")

                get(serverName)
            }
    }

    companion object {
        private val log = logger()
        fun get() = SurfQueueService.instance as RedisQueueService
    }
}