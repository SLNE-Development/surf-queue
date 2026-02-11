package dev.slne.surf.queue.common.redis

import dev.slne.surf.redis.RedisApi
import dev.slne.surf.surfapi.core.api.util.requiredService
import org.jetbrains.annotations.MustBeInvokedByOverriders

abstract class RedisInstance {
    val redisApi = RedisApi.create()

    fun connect() {
        register()
        redisApi.connect()
    }

    @MustBeInvokedByOverriders
    protected open fun register() {

    }

    fun disconnect() {
        redisApi.disconnect()
    }

    companion object {
        val instance = requiredService<RedisInstance>()
        fun get() = instance

        fun namespaced(key: String) = "surf-queue:$key"
    }
}

val redisApi get() = RedisInstance.get().redisApi