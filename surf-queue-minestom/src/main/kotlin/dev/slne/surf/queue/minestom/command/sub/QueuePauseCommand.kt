package dev.slne.surf.queue.minestom.command.sub

import dev.slne.minestom.lobby.api.command.commandapi.CommandAPICommand
import dev.slne.minestom.lobby.api.command.commandapi.dsl.anyExecutorSuspend
import dev.slne.minestom.lobby.api.command.commandapi.dsl.subcommand
import dev.slne.surf.core.api.common.server.SurfServer
import dev.slne.surf.core.api.minestom.command.argument.surfBackendServerArgument
import dev.slne.surf.queue.client.command.QueueMessages
import dev.slne.surf.queue.client.permission.QueuePermissions
import dev.slne.surf.queue.common.queue.RedisQueueService

fun CommandAPICommand.queuePause(): CommandAPICommand = withSubcommand(
    subcommand("pause") {
        withPermission(QueuePermissions.COMMAND_PAUSE)

        withSubcommand(subcommand("pause") {
            surfBackendServerArgument("server", optional = true)
            anyExecutorSuspend { sender, arguments ->
                val server: SurfServer? by arguments
                val serverName = server?.name ?: SurfServer.current().name
                val queue = RedisQueueService.get().getQueueByName(serverName)
                queue.pause()

                sender.sendMessage(QueueMessages.queuePaused())
            }
        })

        withSubcommand(subcommand("resume") {
            surfBackendServerArgument("server", optional = true)
            anyExecutorSuspend { sender, arguments ->
                val server: SurfServer? by arguments
                val serverName = server?.name ?: SurfServer.current().name
                val queue = RedisQueueService.get().getQueueByName(serverName)
                queue.resume()

                sender.sendMessage(QueueMessages.queueResumed())
            }
        })

        withSubcommand(subcommand("status") {
            surfBackendServerArgument("server", optional = true)
            anyExecutorSuspend { sender, arguments ->
                val server: SurfServer? by arguments
                val serverName = server?.name ?: SurfServer.current().name
                val queue = RedisQueueService.get().getQueueByName(serverName)

                sender.sendMessage(QueueMessages.queuePauseStatus(queue.isPaused()))
            }
        })
    }
)
