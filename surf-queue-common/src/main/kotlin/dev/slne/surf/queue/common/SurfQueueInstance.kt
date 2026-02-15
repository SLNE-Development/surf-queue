package dev.slne.surf.queue.common

import dev.slne.surf.queue.common.queue.AbstractSurfQueue
import dev.slne.surf.queue.common.queue.RedisQueueService
import dev.slne.surf.queue.common.redis.RedisInstance
import dev.slne.surf.surfapi.core.api.util.requiredService
import org.jetbrains.annotations.MustBeInvokedByOverriders

abstract class SurfQueueInstance {

    @MustBeInvokedByOverriders
    open suspend fun load() {
        RedisInstance.get().connect()
        RedisQueueService.get().fetchFromRedis()
    }

    @MustBeInvokedByOverriders
    open suspend fun enable() {
    }

    @MustBeInvokedByOverriders
    open suspend fun disable() {
        RedisInstance.get().disconnect()
    }

    abstract fun createQueue(serverName: String): AbstractSurfQueue

    companion object {
        val instance = requiredService<SurfQueueInstance>()
        fun get() = instance
    }
}