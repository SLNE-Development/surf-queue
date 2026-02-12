package dev.slne.surf.queue.velocity.redis.listener

import dev.slne.surf.queue.velocity.plugin
import dev.slne.surf.queue.velocity.redis.packet.TransferPlayerRequest
import dev.slne.surf.queue.velocity.redis.packet.TransferPlayerResponse
import dev.slne.surf.redis.request.HandleRedisRequest
import dev.slne.surf.redis.request.RequestContext
import dev.slne.surf.surfapi.core.api.util.logger
import kotlin.jvm.optionals.getOrNull

class TransferPlayerListener {
    private val log = logger()

    @HandleRedisRequest
    fun onTransferPlayerRequest(context: RequestContext<TransferPlayerRequest>) {
        if (context.originatesFromThisClient()) return
        val request = context.request
        val player = plugin.proxy.getPlayer(request.uuid).getOrNull() ?: return
        val server = plugin.proxy.getServer(request.targetServer).getOrNull()

        if (server == null) {
            context.respond(TransferPlayerResponse(TransferPlayerResponse.Result.ServerNotFound))
        } else {
            player.createConnectionRequest(server)
                .connect()
                .whenComplete { result, throwable ->
                    if (throwable == null) {
                        context.respond(TransferPlayerResponse(TransferPlayerResponse.Result.Success(result.status)))
                    } else {
                        log.atWarning()
                            .withCause(throwable)
                            .log("Error during transfer for player %s", request.uuid)
                        context.respond(TransferPlayerResponse(TransferPlayerResponse.Result.Error))
                    }
                }
        }
    }
}