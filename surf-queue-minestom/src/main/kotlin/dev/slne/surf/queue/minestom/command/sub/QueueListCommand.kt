package dev.slne.surf.queue.minestom.command.sub

import dev.slne.minestom.lobby.api.command.commandapi.CommandAPICommand
import dev.slne.minestom.lobby.api.command.commandapi.dsl.anyExecutorSuspend
import dev.slne.minestom.lobby.api.command.commandapi.dsl.subcommand
import dev.slne.surf.core.api.common.server.SurfServer
import dev.slne.surf.core.api.minestom.command.argument.surfBackendServerArgument
import dev.slne.surf.queue.client.command.QueueMessages
import dev.slne.surf.queue.client.permission.QueuePermissions
import dev.slne.surf.queue.common.queue.RedisQueueService

fun CommandAPICommand.queueList(): CommandAPICommand = withSubcommand(
    subcommand("list") {
        withPermission(QueuePermissions.COMMAND_LIST)
        surfBackendServerArgument("server", optional = true)

        anyExecutorSuspend { sender, arguments ->
            val server: SurfServer? by arguments
            val serverName = server?.name ?: SurfServer.current().name
            val queue = RedisQueueService.get().getQueueByName(serverName)
            val entries = queue.getAllUuidsOrderedByPosition()

            if (entries.isEmpty()) {
                sender.sendMessage(QueueMessages.queueEmpty())
                return@anyExecutorSuspend
            }

            sender.sendMessage(QueueMessages.queueListPagination.renderComponent(entries))
        }
    }
)
