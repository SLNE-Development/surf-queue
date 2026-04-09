package dev.slne.surf.queue.paper.commands.sub

import dev.jorel.commandapi.CommandAPICommand
import dev.jorel.commandapi.arguments.AsyncPlayerProfileArgument
import dev.jorel.commandapi.kotlindsl.argument
import dev.jorel.commandapi.kotlindsl.getValue
import dev.jorel.commandapi.kotlindsl.subcommand
import dev.slne.surf.core.api.common.server.SurfServer
import dev.slne.surf.core.api.paper.command.argument.surfBackendServerArgument
import dev.slne.surf.queue.common.queue.RedisQueueService
import dev.slne.surf.queue.paper.permission.PaperQueuePermissions
import dev.slne.surf.surfapi.bukkit.api.command.executors.anyExecutorSuspend
import dev.slne.surf.surfapi.bukkit.api.command.util.awaitAsyncPlayerProfile
import dev.slne.surf.surfapi.bukkit.api.command.util.idOrThrow
import dev.slne.surf.surfapi.core.api.messages.adventure.sendText

fun CommandAPICommand.queueDequeue() = subcommand("dequeue") {
    withPermission(PaperQueuePermissions.COMMAND_DEQUEUE)
    argument(AsyncPlayerProfileArgument("player"))
    surfBackendServerArgument("server", optional = true)

    anyExecutorSuspend { sender, arguments ->
        val profile = arguments.awaitAsyncPlayerProfile("player")
        val uuid = profile.idOrThrow()
        val server: SurfServer? by arguments
        val serverName = server?.name ?: SurfServer.current().name
        val queue = RedisQueueService.get().get(serverName)

        val dequeued = queue.dequeue(uuid)
        if (dequeued) {
            sender.sendText {
                appendSuccessPrefix()
                success("Removed ")
                variableValue(profile.name ?: uuid.toString())
                success(" from the queue ")
                variableValue(serverName)
                success(".")
            }
        } else {
            sender.sendText {
                appendErrorPrefix()
                error("Player ")
                variableValue(profile.name ?: uuid.toString())
                error(" is not in the queue.")
            }
        }
    }
}