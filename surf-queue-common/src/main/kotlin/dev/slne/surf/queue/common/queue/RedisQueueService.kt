package dev.slne.surf.queue.common.queue

import com.github.benmanes.caffeine.cache.Caffeine
import com.google.auto.service.AutoService
import dev.slne.surf.api.core.util.runAtFixedRate
import dev.slne.surf.api.core.util.withAutoCloseOnRemoval
import dev.slne.surf.queue.api.InternalSurfQueueApi
import dev.slne.surf.queue.api.service.SurfQueueService
import dev.slne.surf.queue.common.QueueInstance
import dev.slne.surf.queue.common.redis.redisApi
import dev.slne.surf.redis.libs.redisson.api.options.KeysScanOptions
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.reactive.collect
import java.lang.AutoCloseable
import java.time.Duration
import kotlin.time.Duration.Companion.seconds

@OptIn(InternalSurfQueueApi::class)
@AutoService(SurfQueueService::class)
class RedisQueueService : SurfQueueService, AutoCloseable {
    private val queues = Caffeine.newBuilder()
        .expireAfterAccess(Duration.ofSeconds(QUEUE_REFRESH_INTERVAL_SECONDS * 4L))
        .withAutoCloseOnRemoval()
        .build<String, AbstractQueue> { serverName ->
            QueueInstance.get().createQueue(serverName).also { queue ->
                if (queue is AbstractTickableQueue) {
                    queue.startTicking()
                }
            }
        }

    private val scope = QueueInstance.get().queueScheduler.createServiceScope()
    private var refreshJob: Job? = null

    override fun getQueueByName(serverName: String) = queues.get(serverName)
    fun getAll() = queues.asMap().values

    fun delete(serverName: String) = queues.invalidate(serverName)

    suspend fun fetchFromRedis() {
        redisApi.redissonReactive.keys
            .getKeys(
                KeysScanOptions.defaults()
                    .pattern(RedisQueueKeys.EPOCH_MS_KEY_PATTERN)
            ).map(::extractServerName)
            .collect(::getQueueByName)
    }

    fun startRefreshing() {
        require(refreshJob == null) { "Refresh job already started" }
        refreshJob = scope.runAtFixedRate(QUEUE_REFRESH_INTERVAL_SECONDS.seconds) {
            fetchFromRedis()
        }
    }

    private fun extractServerName(key: String) = key
        .replaceFirst(RedisQueueKeys.QUEUE_PREFIX, "")
        .replaceFirst(RedisQueueKeys.EPOCH_MS_SUFFIX, "")

    override fun close() {
        scope.cancel("RedisQueueService closed")
        queues.invalidateAll()
    }

    companion object {
        const val QUEUE_REFRESH_INTERVAL_SECONDS = 30
        fun get() = SurfQueueService.instance as RedisQueueService
    }
}