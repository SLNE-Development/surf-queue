package dev.slne.surf.queue.velocity.redis.packet

import dev.slne.surf.redis.request.RedisRequest
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import java.util.*

@Serializable
class TransferPlayerRequest(
    val uuid: @Contextual UUID,
    val targetServer: String
) : RedisRequest()