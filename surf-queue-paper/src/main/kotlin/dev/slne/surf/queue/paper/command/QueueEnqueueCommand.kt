package dev.slne.surf.queue.paper.command

import dev.jorel.commandapi.CommandTree
import dev.jorel.commandapi.arguments.IntegerArgument
import dev.jorel.commandapi.arguments.OfflinePlayerArgument
import dev.jorel.commandapi.kotlindsl.argument
import dev.jorel.commandapi.kotlindsl.literalArgument
import dev.slne.surf.queue.common.queue.RedisQueueService
import dev.slne.surf.queue.paper.permission.SurfQueuePermissions
import dev.slne.surf.surfapi.bukkit.api.command.executors.anyExecutorSuspend
import dev.slne.surf.surfapi.core.api.messages.adventure.sendText
import org.bukkit.Bukkit
import org.bukkit.OfflinePlayer

fun CommandTree.queueEnqueueCommand() = literalArgument("enqueue") {
    withPermission(SurfQueuePermissions.COMMAND_ENQUEUE)

    argument(OfflinePlayerArgument("player")) {
        anyExecutorSuspend { sender, args ->
            val player = args.get("player") as OfflinePlayer
            @Suppress("DEPRECATION")
            val queue = RedisQueueService.get().get(Bukkit.getServerName())

            val added = queue.enqueue(player.uniqueId)
            if (added) {
                sender.sendText {
                    appendSuccessPrefix()
                    success("Enqueued ")
                    variableValue(player.name ?: player.uniqueId.toString())
                    success(" to the queue.")
                }
            } else {
                sender.sendText {
                    appendErrorPrefix()
                    error("Player ")
                    variableValue(player.name ?: player.uniqueId.toString())
                    error(" is already in the queue.")
                }
            }
        }

        argument(IntegerArgument("priority", 0)) {
            anyExecutorSuspend { sender, args ->
                val player = args.get("player") as OfflinePlayer
                val priority = args.get("priority") as Int
                @Suppress("DEPRECATION")
                val queue = RedisQueueService.get().get(Bukkit.getServerName())

                val added = queue.enqueue(player.uniqueId, priority)
                if (added) {
                    sender.sendText {
                        appendSuccessPrefix()
                        success("Enqueued ")
                        variableValue(player.name ?: player.uniqueId.toString())
                        success(" to the queue with priority ")
                        variableValue("$priority")
                        success(".")
                    }
                } else {
                    sender.sendText {
                        appendErrorPrefix()
                        error("Player ")
                        variableValue(player.name ?: player.uniqueId.toString())
                        error(" is already in the queue.")
                    }
                }
            }
        }
    }
}
