package dev.slne.surf.queue.paper.commands.sub

import dev.jorel.commandapi.CommandAPICommand
import dev.jorel.commandapi.arguments.AsyncPlayerProfileArgument
import dev.jorel.commandapi.kotlindsl.argument
import dev.jorel.commandapi.kotlindsl.getValue
import dev.jorel.commandapi.kotlindsl.subcommand
import dev.slne.surf.api.paper.command.executors.anyExecutorSuspend
import dev.slne.surf.api.paper.command.util.awaitAsyncPlayerProfile
import dev.slne.surf.api.paper.command.util.idOrThrow
import dev.slne.surf.core.api.common.server.SurfServer
import dev.slne.surf.core.api.paper.command.argument.surfBackendServerArgument
import dev.slne.surf.queue.client.command.QueueMessages
import dev.slne.surf.queue.common.queue.RedisQueueService
import dev.slne.surf.queue.paper.permission.PaperQueuePermissions

fun CommandAPICommand.queueInfo() = subcommand("info") {
    withPermission(PaperQueuePermissions.COMMAND_INFO)

    argument(AsyncPlayerProfileArgument("player"))
    surfBackendServerArgument("server", optional = true)

    anyExecutorSuspend { sender, arguments ->
        val profile = arguments.awaitAsyncPlayerProfile("player")
        val uuid = profile.idOrThrow()
        val server: SurfServer? by arguments
        val serverName = server?.name ?: SurfServer.current().name
        val queue = RedisQueueService.get().getQueueByName(serverName)
        val playerName = profile.name ?: uuid.toString()

        val isQueued = queue.isQueued(uuid)
        if (!isQueued) {
            sender.sendMessage(QueueMessages.playerNotQueued(playerName))

            return@anyExecutorSuspend
        }

        sender.sendMessage(
            QueueMessages.playerQueueInfo(
                playerName = playerName,
                uuid = uuid,
                position = queue.getPosition(uuid),
                size = queue.size(),
                meta = queue.getEntryMeta(uuid),
                score = queue.getEntryScore(uuid),
                lastSeen = queue.getEntryLastSeen(uuid),
                retryCount = queue.getEntryRetryCount(uuid)
            )
        )
    }
}
