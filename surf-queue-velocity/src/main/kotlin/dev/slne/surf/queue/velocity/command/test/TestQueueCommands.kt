package dev.slne.surf.queue.velocity.command.test

import dev.jorel.commandapi.CommandTree
import dev.jorel.commandapi.kotlindsl.getValue
import dev.jorel.commandapi.kotlindsl.literalArgument
import dev.slne.surf.core.api.common.player.SurfPlayer
import dev.slne.surf.core.api.common.server.SurfServer
import dev.slne.surf.core.api.velocity.command.argument.surfBackendServerArgument
import dev.slne.surf.core.api.velocity.command.argument.surfPlayerArgument
import dev.slne.surf.queue.common.queue.RedisQueueService
import dev.slne.surf.surfapi.core.api.messages.adventure.sendText
import dev.slne.surf.surfapi.velocity.api.command.executors.anyExecutorSuspend

fun CommandTree.testQueueCommands() = literalArgument("test-queue") {

    literalArgument("enqueue") {
        surfPlayerArgument("player") {
            surfBackendServerArgument("server") {
                anyExecutorSuspend { sender, args ->
                    val player: SurfPlayer by args
                    val server: SurfServer by args

                    val queue = RedisQueueService.get().get(server.name)
                    queue.enqueue(player.uuid)
                    sender.sendText {
                        appendSuccessPrefix()
                        success("Enqueued player ")
                        variableValue(player.lastKnownName ?: player.uuid.toString())
                        success(" to queue ")
                        variableValue(server.name)
                    }
                }
            }
        }
    }
}