package dev.slne.surf.queue.paper.commands.sub

import dev.jorel.commandapi.CommandAPICommand
import dev.jorel.commandapi.arguments.AsyncPlayerProfileArgument
import dev.jorel.commandapi.kotlindsl.argument
import dev.jorel.commandapi.kotlindsl.getValue
import dev.jorel.commandapi.kotlindsl.integerArgument
import dev.jorel.commandapi.kotlindsl.subcommand
import dev.slne.surf.api.core.messages.adventure.sendText
import dev.slne.surf.api.paper.command.executors.anyExecutorSuspend
import dev.slne.surf.api.paper.command.util.awaitAsyncPlayerProfile
import dev.slne.surf.api.paper.command.util.idOrThrow
import dev.slne.surf.core.api.common.server.SurfServer
import dev.slne.surf.core.api.paper.command.argument.surfBackendServerArgument
import dev.slne.surf.queue.common.queue.RedisQueueScore
import dev.slne.surf.queue.common.queue.RedisQueueService
import dev.slne.surf.queue.paper.permission.PaperQueuePermissions

fun CommandAPICommand.queueEnqueue() = subcommand("enqueue") {
    withPermission(PaperQueuePermissions.COMMAND_ENQUEUE)

    argument(AsyncPlayerProfileArgument("player"))
    surfBackendServerArgument("server", optional = true)
    integerArgument("priority", optional = true, min = 0, max = RedisQueueScore.MAX_PRIORITY)

    anyExecutorSuspend { sender, arguments ->
        val profile = arguments.awaitAsyncPlayerProfile("player")
        val uuid = profile.idOrThrow()
        val server: SurfServer? by arguments
        val priority: Int? by arguments

        val serverName = server?.name ?: SurfServer.current().name
        val queue = RedisQueueService.get().getQueueByName(serverName)

        val enqueued = priority?.let { queue.enqueue(uuid, it) } ?: queue.enqueue(uuid)
        if (enqueued) {
            sender.sendText {
                appendSuccessPrefix()
                success("Enqueued ")
                variableValue(profile.name ?: uuid.toString())
                success(" to the queue ")
                variableValue(serverName)
                priority?.let {
                    success(" with priority ")
                    variableValue(it)
                }
                success(".")
            }
        } else {
            sender.sendText {
                appendErrorPrefix()
                error("Player ")
                variableValue(profile.name ?: uuid.toString())
                error(" is already in the queue.")
            }
        }
    }
}