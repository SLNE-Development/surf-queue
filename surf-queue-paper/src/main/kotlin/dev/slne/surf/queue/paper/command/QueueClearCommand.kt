package dev.slne.surf.queue.paper.command

import dev.jorel.commandapi.CommandTree
import dev.jorel.commandapi.kotlindsl.literalArgument
import dev.slne.surf.queue.common.queue.RedisQueueService
import dev.slne.surf.queue.paper.permission.SurfQueuePermissions
import dev.slne.surf.queue.paper.queue.PaperSurfQueue
import dev.slne.surf.surfapi.bukkit.api.command.executors.anyExecutorSuspend
import dev.slne.surf.surfapi.core.api.messages.adventure.sendText
import org.bukkit.Bukkit

fun CommandTree.queueClearCommand() = literalArgument("clear") {
    withPermission(SurfQueuePermissions.COMMAND_CLEAR)

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
        queue.delete()

        sender.sendText {
            appendSuccessPrefix()
            success("Cleared the queue. Removed ")
            variableValue("$size")
            success(" entries.")
        }
    }
}
