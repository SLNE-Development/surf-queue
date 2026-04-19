package dev.slne.surf.queue.paper.commands.sub

import dev.jorel.commandapi.CommandAPICommand
import dev.jorel.commandapi.kotlindsl.getValue
import dev.jorel.commandapi.kotlindsl.subcommand
import dev.slne.surf.api.core.messages.adventure.buildText
import dev.slne.surf.api.core.messages.adventure.sendText
import dev.slne.surf.api.core.messages.pagination.Pagination
import dev.slne.surf.api.paper.command.executors.anyExecutorSuspend
import dev.slne.surf.core.api.common.server.SurfServer
import dev.slne.surf.core.api.paper.command.argument.surfBackendServerArgument
import dev.slne.surf.queue.common.queue.RedisQueueService
import dev.slne.surf.queue.paper.permission.PaperQueuePermissions
import java.util.*

private val pagination = Pagination<UUID> {
    rowRenderer { uuid, i ->
        listOf(buildText {
            val position = i + 1
            variableKey(position)
            info("—")
            variableValue(uuid.toString())
        })
    }
}

fun CommandAPICommand.queueList() = subcommand("list") {
    withPermission(PaperQueuePermissions.COMMAND_LIST)
    surfBackendServerArgument("server", optional = true)

    anyExecutorSuspend { sender, arguments ->
        val server: SurfServer? by arguments
        val serverName = server?.name ?: SurfServer.current().name
        val queue = RedisQueueService.get().getQueueByName(serverName)
        val entries = queue.getAllUuidsOrderedByPosition()

        if (entries.isEmpty()) {
            sender.sendText {
                appendInfoPrefix()
                info("The queue is empty.")
            }
            return@anyExecutorSuspend
        }

        sender.sendMessage(pagination.renderComponent(entries))
    }
}