package dev.slne.surf.queue.paper.redis

import com.google.auto.service.AutoService
import dev.slne.surf.queue.common.redis.RedisInstance

@AutoService(RedisInstance::class)
class PaperRedisInstance : RedisInstance()