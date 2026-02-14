package dev.slne.surf.queue.velocity.redis

import com.google.auto.service.AutoService
import dev.slne.surf.queue.common.redis.RedisInstance

@AutoService(RedisInstance::class)
class VelocityRedisInstance : RedisInstance() {
    override fun register() {
        super.register()
        redisApi.registerRequestHandler(TransferPlayerListener())
    }
}