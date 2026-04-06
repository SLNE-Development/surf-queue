package dev.slne.surf.queue.paper.command

import dev.jorel.commandapi.CommandTree
import dev.jorel.commandapi.kotlindsl.literalArgument
import dev.slne.surf.queue.common.queue.RedisQueueService
import dev.slne.surf.queue.paper.permission.SurfQueuePermissions
import dev.slne.surf.queue.paper.queue.PaperSurfQueue
import dev.slne.surf.surfapi.bukkit.api.command.executors.anyExecutorSuspend
import dev.slne.surf.surfapi.core.api.messages.adventure.sendText
import org.bukkit.Bukkit

fun CommandTree.queueStatusCommand() = literalArgument("status") {
    withPermission(SurfQueuePermissions.COMMAND_STATUS)

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

        val size = queue.size()
        val paused = queue.isPaused()
        val tickCount = queue.getTickCount()
        val onlinePlayers = Bukkit.getOnlinePlayers().size
        val maxPlayers = Bukkit.getMaxPlayers()
        val availableSlots = maxPlayers - onlinePlayers

        sender.sendText {
            appendSuccessPrefix()
            success("=== Queue Status: ")
            variableValue(serverName)
            success(" ===")
        }
        sender.sendText {
            success("Size: ")
            variableValue("$size")
        }
        sender.sendText {
            success("Paused: ")
            variableValue(if (paused) "Yes" else "No")
        }
        sender.sendText {
            success("Tick Count: ")
            variableValue("$tickCount")
        }
        sender.sendText {
            success("Server: ")
            variableValue("$onlinePlayers/$maxPlayers")
            success(" (")
            variableValue("$availableSlots")
            success(" slots available)")
        }

        val top = queue.getAllUuidsWithPosition().take(5)
        if (top.isNotEmpty()) {
            sender.sendText {
                success("Top entries:")
            }
            for (entry in top) {
                sender.sendText {
                    spacer("  #")
                    variableValue("${entry.intValue}")
                    success(" - ")
                    variableValue("${entry.key}")
                }
            }
        }
    }
}
