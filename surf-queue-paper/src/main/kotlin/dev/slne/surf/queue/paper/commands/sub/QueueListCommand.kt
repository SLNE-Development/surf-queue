package dev.slne.surf.queue.paper.commands.sub

import dev.jorel.commandapi.CommandAPICommand
import dev.jorel.commandapi.kotlindsl.getValue
import dev.jorel.commandapi.kotlindsl.subcommand
import dev.slne.surf.core.api.common.server.SurfServer
import dev.slne.surf.core.api.paper.command.argument.surfBackendServerArgument
import dev.slne.surf.queue.common.queue.RedisQueueService
import dev.slne.surf.queue.paper.permission.PaperQueuePermissions
import dev.slne.surf.surfapi.bukkit.api.command.executors.anyExecutorSuspend
import dev.slne.surf.surfapi.core.api.messages.adventure.buildText
import dev.slne.surf.surfapi.core.api.messages.adventure.sendText
import dev.slne.surf.surfapi.core.api.messages.pagination.Pagination
import it.unimi.dsi.fastutil.objects.Object2IntMap
import java.util.*

private val pagination = Pagination<Object2IntMap.Entry<UUID>> {
    rowRenderer { value, _ ->
        listOf(buildText {
            val uuid = value.key
            val position = value.intValue
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
        val entries = queue.getAllUuidsWithPosition()

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