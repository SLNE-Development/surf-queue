package dev.slne.surf.queue.common

import dev.slne.surf.api.core.component.SurfComponentApi
import dev.slne.surf.api.core.util.requiredService
import dev.slne.surf.queue.common.queue.AbstractQueue
import dev.slne.surf.queue.common.queue.RedisQueueService
import dev.slne.surf.queue.common.queue.tick.QueueScheduler
import dev.slne.surf.queue.common.redis.RedisInstance
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
    abstract val queueScheduler: QueueScheduler
    abstract val isLoaded: Boolean

    /**
     * Connects to Redis, fetches existing queues, and loads all [dev.slne.surf.api.shared.api.component.SurfComponent]s.
     * Subclasses **must** call `super.load()`.
     */
    @MustBeInvokedByOverriders
    open suspend fun load() {
        RedisInstance.get().connect()
        RedisQueueService.get().startRefreshing()
        SurfComponentApi.load(componentOwner)
    }

    /**
     * Enables all [dev.slne.surf.api.shared.api.component.SurfComponent]s. Subclasses **must** call `super.enable()`.
     */
    @MustBeInvokedByOverriders
    open suspend fun enable() {
        SurfComponentApi.enable(componentOwner)
    }

    /**
     * Stops the [QueueTicker], disables all [dev.slne.surf.api.shared.api.component.SurfComponent]s, and disconnects
     * from Redis. Subclasses **must** call `super.disable()`.
     */
    @MustBeInvokedByOverriders
    open suspend fun disable() {
        RedisQueueService.get().close()

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