package dev.slne.surf.queue.minestom.command.sub

import dev.slne.minestom.lobby.api.command.commandapi.CommandAPICommand
import dev.slne.minestom.lobby.api.command.commandapi.dsl.anyExecutorSuspend
import dev.slne.minestom.lobby.api.command.commandapi.dsl.integerArgument
import dev.slne.minestom.lobby.api.command.commandapi.dsl.subcommand
import dev.slne.surf.core.api.common.player.SurfPlayer
import dev.slne.surf.core.api.common.server.SurfServer
import dev.slne.surf.core.api.minestom.command.argument.surfBackendServerArgument
import dev.slne.surf.core.api.minestom.command.argument.surfOfflinePlayerArgument
import dev.slne.surf.queue.client.command.QueueMessages
import dev.slne.surf.queue.client.permission.QueuePermissions
import dev.slne.surf.queue.common.queue.RedisQueueScore
import dev.slne.surf.queue.common.queue.RedisQueueService
import dev.slne.surf.queue.minestom.command.awaitOrFail
import kotlinx.coroutines.Deferred

fun CommandAPICommand.queueEnqueue(): CommandAPICommand = withSubcommand(
    subcommand("enqueue") {
        withPermission(QueuePermissions.COMMAND_ENQUEUE)

        surfOfflinePlayerArgument("player")
        surfBackendServerArgument("server", optional = true)
        integerArgument("priority", min = 0, max = RedisQueueScore.MAX_PRIORITY, optional = true)

        anyExecutorSuspend { sender, arguments ->
            val player: Deferred<SurfPlayer?> by arguments
            val target = player.awaitOrFail()
            val server: SurfServer? by arguments
            val priority: Int? by arguments

            val serverName = server?.name ?: SurfServer.current().name
            val queue = RedisQueueService.get().getQueueByName(serverName)
            val playerName = target.lastKnownName ?: target.uuid.toString()

            val enqueued = priority?.let { queue.enqueue(target.uuid, it) } ?: queue.enqueue(target.uuid)
            if (enqueued) {
                sender.sendMessage(QueueMessages.playerEnqueued(playerName, serverName, priority))
            } else {
                sender.sendMessage(QueueMessages.playerAlreadyQueued(playerName))
            }
        }
    }
)
