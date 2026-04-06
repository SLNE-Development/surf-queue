package dev.slne.surf.queue.paper.command

import dev.jorel.commandapi.CommandTree
import dev.jorel.commandapi.kotlindsl.literalArgument
import dev.slne.surf.queue.common.queue.RedisQueueService
import dev.slne.surf.queue.paper.permission.SurfQueuePermissions
import dev.slne.surf.queue.paper.queue.PaperSurfQueue
import dev.slne.surf.surfapi.bukkit.api.command.executors.anyExecutorSuspend
import dev.slne.surf.surfapi.core.api.messages.adventure.sendText
import org.bukkit.Bukkit

fun CommandTree.queueListCommand() = literalArgument("list") {
    withPermission(SurfQueuePermissions.COMMAND_LIST)

    anyExecutorSuspend { sender, _ ->
        @Suppress("DEPRECATION")
        val serverName = Bukkit.getServerName()
        val queue = RedisQueueService.get().get(serverName) as? PaperSurfQueue

        if (queue == null) {
            sender.sendText {
                appendErrorPrefix()
                error("No queue found for this server.")
            }
            return@anyExecutorSuspend
        }

        val entries = queue.getAllUuidsWithPosition()

        if (entries.isEmpty()) {
            sender.sendText {
                appendInfoPrefix()
                info("The queue is empty.")
            }
            return@anyExecutorSuspend
        }

        sender.sendText {
            appendSuccessPrefix()
            success("=== Queue Entries (")
            variableValue("${entries.size}")
            success(") ===")
        }

        for (entry in entries) {
            val uuid = entry.key
            val position = entry.intValue

            sender.sendText {
                spacer("  #")
                variableValue("$position")
                success(" - ")
                variableValue("$uuid")
            }
        }
    }
}
