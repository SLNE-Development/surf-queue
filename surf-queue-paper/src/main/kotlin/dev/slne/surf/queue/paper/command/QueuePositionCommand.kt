package dev.slne.surf.queue.paper.command

import dev.jorel.commandapi.CommandTree
import dev.jorel.commandapi.arguments.OfflinePlayerArgument
import dev.jorel.commandapi.kotlindsl.argument
import dev.jorel.commandapi.kotlindsl.literalArgument
import dev.slne.surf.queue.common.queue.RedisQueueService
import dev.slne.surf.queue.paper.permission.SurfQueuePermissions
import dev.slne.surf.surfapi.bukkit.api.command.executors.anyExecutorSuspend
import dev.slne.surf.surfapi.core.api.messages.adventure.sendText
import org.bukkit.Bukkit
import org.bukkit.OfflinePlayer

fun CommandTree.queuePositionCommand() = literalArgument("position") {
    withPermission(SurfQueuePermissions.COMMAND_POSITION)

    argument(OfflinePlayerArgument("player")) {
        anyExecutorSuspend { sender, args ->
            val player = args.get("player") as OfflinePlayer
            @Suppress("DEPRECATION")
            val queue = RedisQueueService.get().get(Bukkit.getServerName())

            val position = queue.getPosition(player.uniqueId)
            val size = queue.size()

            if (position != null) {
                sender.sendText {
                    appendInfoPrefix()
                    variableValue(player.name ?: player.uniqueId.toString())
                    info(" is at position ")
                    variableValue("${position + 1}")
                    info(" of ")
                    variableValue("$size")
                    info(".")
                }
            } else {
                sender.sendText {
                    appendErrorPrefix()
                    error("Player ")
                    variableValue(player.name ?: player.uniqueId.toString())
                    error(" is not in the queue.")
                }
            }
        }
    }
}
