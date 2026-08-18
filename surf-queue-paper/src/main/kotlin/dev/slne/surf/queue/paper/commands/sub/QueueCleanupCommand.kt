package dev.slne.surf.queue.paper.commands.sub

import dev.jorel.commandapi.CommandAPICommand
import dev.jorel.commandapi.kotlindsl.getValue
import dev.jorel.commandapi.kotlindsl.subcommand
import dev.slne.surf.api.paper.command.executors.anyExecutorSuspend
import dev.slne.surf.core.api.common.server.SurfServer
import dev.slne.surf.core.api.paper.command.argument.surfBackendServerArgument
import dev.slne.surf.queue.client.command.QueueMessages
import dev.slne.surf.queue.client.queue.ClientQueue
import dev.slne.surf.queue.common.queue.RedisQueueService
import dev.slne.surf.queue.paper.permission.PaperQueuePermissions

fun CommandAPICommand.queueCleanup() = subcommand("cleanup") {
    withPermission(PaperQueuePermissions.COMMAND_CLEANUP)
    surfBackendServerArgument("server", optional = true)

    anyExecutorSuspend { sender, arguments ->
        val server: SurfServer? by arguments
        val serverName = server?.name ?: SurfServer.current().name
        val queue = RedisQueueService.get().getQueueByName(serverName) as ClientQueue

        val sizeBefore = queue.size()
        queue.forceCleanup()
        val sizeAfter = queue.size()

        sender.sendMessage(QueueMessages.cleanupDone(serverName, sizeBefore, sizeAfter))
    }
}
