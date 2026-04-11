package dev.slne.surf.queue.common

import dev.slne.surf.queue.common.queue.AbstractQueue
import dev.slne.surf.queue.common.queue.QueueTicker
import dev.slne.surf.queue.common.queue.RedisQueueService
import dev.slne.surf.queue.common.redis.RedisInstance
import dev.slne.surf.surfapi.core.api.component.SurfComponentApi
import dev.slne.surf.surfapi.core.api.util.requiredService
import org.jetbrains.annotations.MustBeInvokedByOverriders

abstract class QueueInstance { // Implementations are responsible for starting the queue ticker task
    protected abstract val componentOwner: Any

    @MustBeInvokedByOverriders
    open suspend fun load() {
        RedisInstance.get().connect()
        RedisQueueService.get().fetchFromRedis()
        SurfComponentApi.load(componentOwner)
    }

    @MustBeInvokedByOverriders
    open suspend fun enable() {
        SurfComponentApi.enable(componentOwner)
    }

    @MustBeInvokedByOverriders
    open suspend fun disable() {
        QueueTicker.dispose()

        SurfComponentApi.disable(componentOwner)
        RedisInstance.get().disconnect()
    }

    abstract fun createQueue(serverName: String): AbstractQueue

    companion object {
        val instance = requiredService<QueueInstance>()
        fun get() = instance
    }
}