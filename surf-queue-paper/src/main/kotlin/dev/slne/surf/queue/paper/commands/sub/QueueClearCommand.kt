package dev.slne.surf.queue.paper.commands.sub

import dev.jorel.commandapi.CommandAPICommand
import dev.jorel.commandapi.kotlindsl.getValue
import dev.jorel.commandapi.kotlindsl.subcommand
import dev.slne.surf.core.api.common.server.SurfServer
import dev.slne.surf.core.api.paper.command.argument.surfBackendServerArgument
import dev.slne.surf.queue.common.queue.RedisQueueService
import dev.slne.surf.queue.paper.permission.PaperQueuePermissions
import dev.slne.surf.queue.paper.queue.PaperQueueImpl
import dev.slne.surf.surfapi.bukkit.api.command.executors.anyExecutorSuspend
import dev.slne.surf.surfapi.core.api.messages.adventure.sendText

/** Registers the `clear` subcommand that deletes all entries from a queue. */
fun CommandAPICommand.queueClear() = subcommand("clear") {
    withPermission(PaperQueuePermissions.COMMAND_CLEAR)
    surfBackendServerArgument("server", optional = true)

    anyExecutorSuspend { sender, arguments ->
        val server: SurfServer? by arguments
        val serverName = server?.name ?: SurfServer.current().name
        val queue = RedisQueueService.get().get(serverName) as PaperQueueImpl

        val size = queue.size()
        queue.delete()

        sender.sendText {
            appendSuccessPrefix()
            success("Cleared queue for server ")
            variableValue(serverName)
            success(". Removed ")
            variableValue("$size")
            success(" entries.")
        }
    }
}