package dev.slne.surf.queue.common

import dev.slne.surf.queue.common.queue.AbstractQueue
import dev.slne.surf.queue.common.queue.QueueTicker
import dev.slne.surf.queue.common.queue.RedisQueueService
import dev.slne.surf.queue.common.redis.RedisInstance
import dev.slne.surf.surfapi.core.api.component.SurfComponentApi
import dev.slne.surf.surfapi.core.api.util.requiredService
import org.jetbrains.annotations.MustBeInvokedByOverriders

/**
 * Abstract base class that bootstraps the queue system.
 *
 * Responsible for connecting to Redis, loading queue state, managing
 * [SurfComponentApi] lifecycle, and tearing down the [QueueTicker].
 * Platform-specific subclasses (Paper, Velocity) provide the [componentOwner]
 * and implement [createQueue] to return the appropriate [AbstractQueue] variant.
 *
 * Implementations are responsible for starting the [QueueTicker] at the
 * appropriate time during their platform's lifecycle.
 */
abstract class QueueInstance { // Implementations are responsible for starting the queue ticker task
    /** The platform-specific owner object used with [SurfComponentApi]. */
    protected abstract val componentOwner: Any

    /**
     * Connects to Redis, fetches existing queues, and loads all [SurfComponent]s.
     * Subclasses **must** call `super.load()`.
     */
    @MustBeInvokedByOverriders
    open suspend fun load() {
        RedisInstance.get().connect()
        RedisQueueService.get().fetchFromRedis()
        SurfComponentApi.load(componentOwner)
    }

    /**
     * Enables all [SurfComponent]s. Subclasses **must** call `super.enable()`.
     */
    @MustBeInvokedByOverriders
    open suspend fun enable() {
        SurfComponentApi.enable(componentOwner)
    }

    /**
     * Stops the [QueueTicker], disables all [SurfComponent]s, and disconnects
     * from Redis. Subclasses **must** call `super.disable()`.
     */
    @MustBeInvokedByOverriders
    open suspend fun disable() {
        QueueTicker.dispose()

        SurfComponentApi.disable(componentOwner)
        RedisInstance.get().disconnect()
    }

    /**
     * Factory method to create a new [AbstractQueue] for the given [serverName].
     *
     * @param serverName the name of the target server
     * @return a platform-specific queue implementation
     */
    abstract fun createQueue(serverName: String): AbstractQueue

    companion object {
        val instance = requiredService<QueueInstance>()
        fun get() = instance
    }
}