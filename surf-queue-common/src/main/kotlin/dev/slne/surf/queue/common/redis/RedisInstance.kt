package dev.slne.surf.queue.common.redis

import com.google.auto.service.AutoService
import dev.slne.surf.redis.RedisApi
import dev.slne.surf.surfapi.core.api.util.requiredService
import net.kyori.adventure.util.Services
import org.jetbrains.annotations.MustBeInvokedByOverriders

/**
 * Manages the Redis connection used by the queue system.
 *
 * Subclasses may override [register] to register custom codecs or other
 * Redisson configuration before the connection is frozen. A [Fallback]
 * implementation is provided via `@AutoService` for environments where no
 * platform-specific registration is needed.
 */
abstract class RedisInstance {
    /** The underlying [RedisApi] instance used for all Redis operations. */
    val redisApi = RedisApi.create()

    /**
     * Registers any custom configuration and then freezes and connects the [redisApi].
     */
    fun connect() {
        register()
        redisApi.freezeAndConnect()
    }

    /**
     * Hook for subclasses to register codecs or other Redisson configuration
     * before the connection is established. Called by [connect].
     */
    @MustBeInvokedByOverriders
    protected open fun register() {

    }

    /** Disconnects the [redisApi] from Redis. */
    fun disconnect() {
        redisApi.disconnect()
    }

    companion object {
        /** The singleton [RedisInstance], loaded via `requiredService`. */
        val instance = requiredService<RedisInstance>()

        /** Returns the singleton instance. */
        fun get() = instance

        /**
         * Prefixes [key] with the `surf-queue:` namespace.
         *
         * @param key the raw key
         * @return the namespaced key
         */
        fun namespaced(key: String) = "surf-queue:$key"

        /** No-op fallback implementation used when no platform-specific [RedisInstance] is registered. */
        @AutoService(RedisInstance::class)
        class Fallback : RedisInstance(), Services.Fallback
    }
}

/** Top-level accessor for the [RedisApi] instance managed by [RedisInstance]. */
val redisApi get() = RedisInstance.get().redisApi