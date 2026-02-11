package dev.slne.surf.queue.common

import dev.slne.surf.queue.common.redis.RedisInstance
import dev.slne.surf.surfapi.core.api.util.requiredService
import org.jetbrains.annotations.MustBeInvokedByOverriders

abstract class SurfQueueInstance {

    @MustBeInvokedByOverriders
    open suspend fun load() {

    }

    @MustBeInvokedByOverriders
    open suspend fun enable() {
        RedisInstance.get().connect()
    }

    @MustBeInvokedByOverriders
    open suspend fun disable() {
        RedisInstance.get().disconnect()
    }

    companion object {
        val instance = requiredService<SurfQueueInstance>()
        fun get() = instance
    }
}