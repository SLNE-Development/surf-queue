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

fun CommandAPICommand.queueCleanup() = subcommand("cleanup") {
    withPermission(PaperQueuePermissions.COMMAND_CLEANUP)
    surfBackendServerArgument("server", optional = true)

    anyExecutorSuspend { sender, arguments ->
        val server: SurfServer? by arguments
        val serverName = server?.name ?: SurfServer.current().name
        val queue = RedisQueueService.get().getQueueByName(serverName) as PaperQueueImpl

        val sizeBefore = queue.size()
        queue.forceCleanup()
        val sizeAfter = queue.size()
        val removed = sizeBefore - sizeAfter

        sender.sendText {
            appendSuccessPrefix()
            success("Forced cleanup of queue '")
            variableValue(serverName)
            success("' complete. Removed ")
            variableValue("$removed")
            success(" expired entries (")
            variableValue("$sizeBefore")
            success(" —> ")
            variableValue("$sizeAfter")
            success(").")
        }
    }
}