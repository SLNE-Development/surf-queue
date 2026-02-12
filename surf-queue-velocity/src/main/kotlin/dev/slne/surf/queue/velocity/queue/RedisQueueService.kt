package dev.slne.surf.queue.velocity.queue

import com.github.benmanes.caffeine.cache.Caffeine

object RedisQueueService {
    private val queues = Caffeine.newBuilder()
        .build<String, RedisQueue> { serverName -> RedisQueue(serverName) }

    fun get(serverName: String) = queues.get(serverName)
    fun getAll() = queues.asMap().values

    fun delete(serverName: String) = queues.invalidate(serverName)
}