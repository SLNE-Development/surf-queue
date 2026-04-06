package dev.slne.surf.queue.paper.command

import dev.jorel.commandapi.CommandTree
import dev.jorel.commandapi.kotlindsl.literalArgument
import dev.slne.surf.queue.common.queue.RedisQueueService
import dev.slne.surf.queue.paper.permission.SurfQueuePermissions
import dev.slne.surf.queue.paper.queue.PaperSurfQueue
import dev.slne.surf.surfapi.bukkit.api.command.executors.anyExecutorSuspend
import dev.slne.surf.surfapi.core.api.messages.adventure.sendText
import org.bukkit.Bukkit

fun CommandTree.queueCleanupCommand() = literalArgument("cleanup") {
    withPermission(SurfQueuePermissions.COMMAND_CLEANUP)

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

        val sizeBefore = queue.size()
        queue.forceCleanup()
        val sizeAfter = queue.size()
        val removed = sizeBefore - sizeAfter

        sender.sendText {
            appendSuccessPrefix()
            success("Forced cleanup complete. Removed ")
            variableValue("$removed")
            success(" expired entries (")
            variableValue("$sizeBefore")
            success(" → ")
            variableValue("$sizeAfter")
            success(").")
        }
    }
}
