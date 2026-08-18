package dev.slne.surf.queue.minestom.command.sub

import dev.slne.minestom.lobby.api.command.commandapi.CommandAPICommand
import dev.slne.minestom.lobby.api.command.commandapi.dsl.anyExecutorSuspend
import dev.slne.minestom.lobby.api.command.commandapi.dsl.subcommand
import dev.slne.surf.core.api.common.player.SurfPlayer
import dev.slne.surf.core.api.common.server.SurfServer
import dev.slne.surf.core.api.minestom.command.argument.surfBackendServerArgument
import dev.slne.surf.core.api.minestom.command.argument.surfOfflinePlayerArgument
import dev.slne.surf.queue.client.command.QueueMessages
import dev.slne.surf.queue.client.permission.QueuePermissions
import dev.slne.surf.queue.common.queue.RedisQueueService
import dev.slne.surf.queue.minestom.command.awaitOrFail
import kotlinx.coroutines.Deferred

fun CommandAPICommand.queueInfo(): CommandAPICommand = withSubcommand(
    subcommand("info") {
        withPermission(QueuePermissions.COMMAND_INFO)

        surfOfflinePlayerArgument("player")
        surfBackendServerArgument("server", optional = true)

        anyExecutorSuspend { sender, arguments ->
            val player: Deferred<SurfPlayer?> by arguments
            val target = player.awaitOrFail()
            val server: SurfServer? by arguments
            val serverName = server?.name ?: SurfServer.current().name
            val queue = RedisQueueService.get().getQueueByName(serverName)
            val uuid = target.uuid
            val playerName = target.lastKnownName ?: uuid.toString()

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
)
