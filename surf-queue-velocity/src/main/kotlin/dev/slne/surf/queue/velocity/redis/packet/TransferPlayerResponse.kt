package dev.slne.surf.queue.velocity.redis.packet

import com.velocitypowered.api.proxy.ConnectionRequestBuilder
import dev.slne.surf.redis.request.RedisResponse
import kotlinx.serialization.Serializable

@Serializable
class TransferPlayerResponse(
    val result: Result
) : RedisResponse() {
    sealed interface Result {
        data class Success(val status: ConnectionRequestBuilder.Status) : Result
        data object ServerNotFound : Result
        data object Error : Result
    }
}
